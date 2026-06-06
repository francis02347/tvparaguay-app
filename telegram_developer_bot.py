import os
import sys
import json
import time
import requests
import subprocess
import traceback
import base64
import io

# Forzar salida estándar y error en UTF-8 en Windows para evitar errores charmap al imprimir emojis
if sys.platform.startswith('win'):
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace', line_buffering=True, write_through=True)
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace', line_buffering=True, write_through=True)

CONFIG_FILE = "bot_config.json"

def load_config():
    if not os.path.exists(CONFIG_FILE):
        print(f"[-] ERROR: No se encontró el archivo '{CONFIG_FILE}'.")
        print("[*] Por favor, copia 'bot_config.json.template' a 'bot_config.json' y completa tus credenciales.")
        sys.exit(1)
    
    try:
        with open(CONFIG_FILE, "r", encoding="utf-8") as f:
            config = json.load(f)
        
        required = ["TELEGRAM_BOT_TOKEN", "ALLOWED_CHAT_ID", "GEMINI_API_KEY"]
        for key in required:
            if key not in config or not config[key] or config[key] == f"TU_{key}_AQUI":
                raise ValueError(f"La clave '{key}' no está configurada correctamente en '{CONFIG_FILE}'.")
                
        return config
    except Exception as e:
        print(f"[-] ERROR al leer '{CONFIG_FILE}': {e}")
        sys.exit(1)

config = load_config()
BOT_TOKEN = config["TELEGRAM_BOT_TOKEN"]
ALLOWED_CHAT_ID = int(config["ALLOWED_CHAT_ID"])
GEMINI_KEY = config["GEMINI_API_KEY"]
GEMINI_MODEL = "gemini-3.1-flash-lite"  # Modelo rápido, eficiente y con amplia ventana de contexto

# Diccionario en memoria para almacenar el historial de chat de cada sesión
sessions = {}

# Definición de herramientas para la API de Gemini
GEMINI_TOOLS = [{
    "functionDeclarations": [
        {
            "name": "list_directory",
            "description": "Lists the files and directories inside the given dir_path (relative to project root).",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "dir_path": {
                        "type": "STRING",
                        "description": "The directory path. Defaults to '.' if empty."
                    }
                }
            }
        },
        {
            "name": "read_file",
            "description": "Reads the entire text content of a file.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "file_path": {
                        "type": "STRING",
                        "description": "The path to the file to read (relative to project root)."
                    }
                },
                "required": ["file_path"]
            }
        },
        {
            "name": "write_file",
            "description": "Writes content to a file, overwriting its existing content or creating it.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "file_path": {
                        "type": "STRING",
                        "description": "The path to the file to write (relative to project root)."
                    },
                    "content": {
                        "type": "STRING",
                        "description": "The complete new content of the file."
                    }
                },
                "required": ["file_path", "content"]
            }
        },
        {
            "name": "search_text",
            "description": "Searches for a specific text query inside files recursively, ignoring binary or build folders.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "query": {
                        "type": "STRING",
                        "description": "The string to search for."
                    },
                    "search_path": {
                        "type": "STRING",
                        "description": "The directory to search inside. Defaults to '.'."
                    }
                },
                "required": ["query"]
            }
        },
        {
            "name": "execute_command",
            "description": "Executes a shell command on the local computer (like gradlew assembleDebug, git status, git add, git commit, etc.) and returns stdout and stderr.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "command": {
                        "type": "STRING",
                        "description": "The command string to execute."
                    }
                },
                "required": ["command"]
            }
        },
        {
            "name": "send_file_to_user",
            "description": "Sends a file (e.g. an APK, log, or image) from the local machine back to the user's Telegram chat.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "file_path": {
                        "type": "STRING",
                        "description": "The path to the file to send (relative to project root)."
                    }
                },
                "required": ["file_path"]
            }
        },
        {
            "name": "read_web_page",
            "description": "Downloads and reads the raw text content of a given URL (e.g. to inspect a website for streaming links).",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "url": {
                        "type": "STRING",
                        "description": "The URL to download (e.g. http://example.com/live)."
                    }
                },
                "required": ["url"]
            }
        },
        {
            "name": "search_web",
            "description": "Searches the web using Google Search to find information, news, or live streaming links.",
            "parameters": {
                "type": "OBJECT",
                "properties": {
                    "query": {
                        "type": "STRING",
                        "description": "The search query (e.g. 'RTVE La 1 directo m3u8')."
                    }
                },
                "required": ["query"]
            }
        }
    ]
}]

SYSTEM_INSTRUCTION = {
    "parts": [{
        "text": """You are an AI coding assistant running locally on the user's computer as a Telegram bot. 
Your goal is to help the user manage their Android application project, make changes to code, run terminal commands (such as git, gradlew, gradle, python scripts), and send files (like compiled APKs) back to the user.

Rules:
1. Always be precise when modifying files. Read a file first to understand its contents before writing changes.
2. When the user asks to compile or build the app, execute the appropriate command (usually 'gradlew assembleDebug' or 'gradlew.bat' on Windows) and then, when completed, use the 'send_file_to_user' tool to send the generated APK. In Android projects, the debug APK is typically generated at: 'app/build/outputs/apk/debug/app-debug.apk'.
3. Always explain what you did or what tools you ran in your final response.
4. Keep your responses concise and in Spanish, as the user speaks Spanish.
5. If a command fails, report the error and try to debug it.
6. Only call tools when necessary. If the user's request is purely conversational, reply directly without calling any tools.
7. Use function calling to inspect files, edit them, run commands, and send files back. Keep looping until the task is complete.
"""
    }]
}

def send_message(chat_id, text, reply_to_message_id=None):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage"
    payload = {
        "chat_id": chat_id,
        "text": text,
        "parse_mode": "Markdown"
    }
    if reply_to_message_id:
        payload["reply_to_message_id"] = reply_to_message_id
    
    try:
        res = requests.post(url, json=payload, timeout=10)
        res_json = res.json()
        if not res_json.get("ok"):
            # Fallback a texto plano si falla el parseo de Markdown
            payload.pop("parse_mode", None)
            res = requests.post(url, json=payload, timeout=10)
            res_json = res.json()
        return res_json
    except Exception as e:
        print(f"[-] Error al enviar mensaje: {e}")
        return {"ok": False}

def send_document(chat_id, file_path):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/sendDocument"
    try:
        with open(file_path, "rb") as f:
            files = {"document": f}
            data = {"chat_id": chat_id}
            res = requests.post(url, data=data, files=files, timeout=60)
            return res.json()
    except Exception as e:
        print(f"[-] Error al enviar documento: {e}")
        return {"ok": False, "description": str(e)}

def handle_function_call(name, args, chat_id):
    print(f"[*] Ejecutando herramienta: {name} con argumentos: {args}")
    
    # Resolver ruta absoluta y validar que esté en el directorio de trabajo (seguridad básica)
    cwd = os.getcwd()
    
    if name == "list_directory":
        dir_path = args.get("dir_path", ".")
        send_message(chat_id, f"📁 *Listando directorio:* `{dir_path}`...")
        real_path = os.path.abspath(dir_path)
        if not real_path.startswith(cwd):
            return {"error": "Acceso denegado. No puedes listar directorios fuera de la raíz del proyecto."}
        try:
            items = []
            for item in os.listdir(real_path):
                # Omitir carpetas pesadas o privadas para no saturar
                if item in (".git", ".gradle", ".idea", "build"):
                    continue
                is_dir = os.path.isdir(os.path.join(real_path, item))
                items.append({"name": item, "type": "directory" if is_dir else "file"})
            return {"files": items}
        except Exception as e:
            return {"error": str(e)}

    elif name == "read_file":
        file_path = args.get("file_path")
        send_message(chat_id, f"📖 *Leyendo archivo:* `{file_path}`...")
        real_path = os.path.abspath(file_path)
        if not real_path.startswith(cwd):
            return {"error": "Acceso denegado. No puedes leer archivos fuera de la raíz del proyecto."}
        try:
            # Limitar lectura a 150KB para evitar exceder el límite de tokens
            if not os.path.exists(real_path):
                return {"error": f"El archivo '{file_path}' no existe."}
            with open(real_path, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read(150000)
            return {"content": content}
        except Exception as e:
            return {"error": str(e)}

    elif name == "write_file":
        file_path = args.get("file_path")
        content = args.get("content")
        send_message(chat_id, f"💾 *Guardando cambios en:* `{file_path}`...")
        real_path = os.path.abspath(file_path)
        if not real_path.startswith(cwd):
            return {"error": "Acceso denegado. No puedes modificar archivos fuera de la raíz del proyecto."}
        try:
            os.makedirs(os.path.dirname(real_path), exist_ok=True)
            with open(real_path, "w", encoding="utf-8") as f:
                f.write(content)
            return {"status": "success", "message": f"Archivo '{file_path}' escrito con éxito."}
        except Exception as e:
            return {"error": str(e)}

    elif name == "search_text":
        query = args.get("query")
        search_path = args.get("search_path", ".")
        send_message(chat_id, f"🔎 *Buscando texto:* `{query}` en `{search_path}`...")
        real_path = os.path.abspath(search_path)
        if not real_path.startswith(cwd):
            return {"error": "Acceso denegado. No puedes buscar fuera de la raíz del proyecto."}
        try:
            results = []
            for root, dirs, files in os.walk(real_path):
                # Omitir directorios pesados/temporales
                dirs[:] = [d for d in dirs if d not in (".git", ".gradle", ".idea", "build")]
                for file in files:
                    full_path = os.path.join(root, file)
                    try:
                        with open(full_path, "r", encoding="utf-8", errors="ignore") as f:
                            for i, line in enumerate(f, 1):
                                if query.lower() in line.lower():
                                    rel_path = os.path.relpath(full_path, cwd)
                                    results.append({"file": rel_path, "line": i, "content": line.strip()})
                                    if len(results) >= 30:
                                        return {"results": results, "note": "Búsqueda limitada a los primeros 30 resultados."}
                    except:
                        pass
            return {"results": results}
        except Exception as e:
            return {"error": str(e)}

    elif name == "execute_command":
        command = args.get("command")
        send_message(chat_id, f"⚡ *Ejecutando comando localmente:*\n`{command}`")
        try:
            # Ejecutar comando en la shell en el directorio actual
            process = subprocess.run(
                command,
                shell=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                stdin=subprocess.DEVNULL, # Evitar bloqueos interactivos (como Read-Host)
                text=True,
                cwd=cwd,
                timeout=450  # 7.5 minutos máximo por si compila
            )
            # Acortar salida para el contexto de la IA
            stdout = process.stdout[-8000:] if process.stdout else ""
            stderr = process.stderr[-8000:] if process.stderr else ""
            return {
                "exit_code": process.returncode,
                "stdout": stdout,
                "stderr": stderr
            }
        except subprocess.TimeoutExpired:
            return {"error": "El comando excedió el tiempo límite de ejecución (7.5 minutos)."}
        except Exception as e:
            return {"error": str(e)}

    elif name == "send_file_to_user":
        file_path = args.get("file_path")
        real_path = os.path.abspath(file_path)
        if not real_path.startswith(cwd):
            return {"error": "Acceso denegado. No puedes enviar archivos fuera del proyecto."}
        
        # Búsqueda inteligente de APK si no se encuentra exactamente
        if not os.path.exists(real_path) and file_path.endswith(".apk"):
            print("[*] Archivo no encontrado. Buscando APK en la carpeta de build...")
            apks = []
            for root, dirs, files in os.walk(os.path.join(cwd, "app", "build")):
                for f in files:
                    if f.endswith(".apk"):
                        apks.append(os.path.join(root, f))
            if apks:
                # Tomar la primera APK encontrada (suele ser la más nueva o debug)
                real_path = apks[0]
                file_path = os.path.relpath(real_path, cwd)
            else:
                return {"error": f"No se encontró el archivo '{file_path}' ni ningún APK generado en la compilación."}
        
        if not os.path.exists(real_path):
            return {"error": f"El archivo '{file_path}' no existe."}
            
        send_message(chat_id, f"📤 *Enviando archivo:* `{file_path}`...")
        res = send_document(chat_id, real_path)
        if res.get("ok"):
            return {"status": "success", "message": f"Archivo '{file_path}' enviado con éxito al usuario."}
        else:
            return {"error": f"Error de Telegram al enviar el archivo: {res.get('description')}"}

    elif name == "read_web_page":
        url = args.get("url")
        send_message(chat_id, f"🌐 *Leyendo página web:* `{url}`...")
        if not url.startswith(("http://", "https://")):
            return {"error": "URL inválida. Debe comenzar con http:// o https://"}
        try:
            import re
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            }
            res = requests.get(url, headers=headers, timeout=15)
            # Remover scripts, estilos y HTML
            text = re.sub(r'<script[^>]*>([\s\S]*?)</script>', '', res.text)
            text = re.sub(r'<style[^>]*>([\s\S]*?)</style>', '', text)
            text = re.sub(r'<[^>]+>', ' ', text)
            text = re.sub(r'\s+', ' ', text).strip()
            return {"url": url, "content": text[:100000]}
        except Exception as e:
            return {"error": str(e)}

    elif name == "search_web":
        query = args.get("query")
        send_message(chat_id, f"🔍 *Buscando en la web:* `{query}`...")
        import urllib.parse
        import re
        
        results = []
        
        # 1. Búsqueda específica en GitHub (Si la consulta es sobre IPTV o canales de TV)
        is_iptv_query = any(kw in query.lower() for kw in ["m3u", "iptv", "channel", "canal", "lista", "stream", "github", "television", "transmision"])
        if is_iptv_query:
            try:
                print(f"[*] Detectada consulta sobre IPTV/Canales. Buscando en la API de GitHub primero...")
                github_url = f"https://api.github.com/search/repositories?q={urllib.parse.quote(query)}"
                headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
                gh_res = requests.get(github_url, headers=headers, timeout=8)
                if gh_res.status_code == 200:
                    gh_data = gh_res.json()
                    for repo in gh_data.get("items", [])[:8]:
                        results.append({
                            "title": f"Repositorio GitHub: {repo.get('full_name')}",
                            "url": repo.get("html_url"),
                            "description": repo.get("description", "Sin descripción")
                        })
                    print(f"[*] API de GitHub devolvió {len(results)} repositorios relacionados.")
            except Exception as gh_ex:
                print(f"[-] Error en la API de GitHub: {gh_ex}")
                
        # 2. Si no es de IPTV o no obtuvimos resultados, intentamos SearXNG (JSON)
        if not results:
            searx_instances = [
                "https://searx.be/search",
                "https://search.mdelta.me/search",
                "https://searx.ch/search",
                "https://priv.au/search",
                "https://opnxng.com/search",
                "https://grep.vim.wtf/search",
                "https://copp.gg/search",
                "https://etsi.me/search",
                "https://kantan.cat/search"
            ]
            for instance in searx_instances:
                try:
                    print(f"[*] Intentando buscar en SearXNG: {instance}")
                    params = {
                        "q": query,
                        "format": "json",
                        "language": "es"
                    }
                    headers = {
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/120.0.0.0"
                    }
                    res = requests.get(instance, params=params, headers=headers, timeout=6)
                    if res.status_code == 200:
                        data = res.json()
                        raw_results = data.get("results", [])
                        for item in raw_results:
                            if item.get("url"):
                                results.append({
                                    "title": item.get("title", "Sin título"),
                                    "url": item.get("url"),
                                    "description": item.get("content", "")
                                })
                                if len(results) >= 8:
                                    break
                        if results:
                            print(f"[*] Éxito al buscar en SearXNG ({instance}). Resultados: {len(results)}")
                            break
                except Exception as e:
                    print(f"[-] Error al consultar instancia SearXNG {instance}: {e}")
                    
        # 3. Fallback a DuckDuckGo Lite con UA móvil
        if not results:
            try:
                print("[*] Fallback: Intentando DuckDuckGo Lite...")
                url_lite = "https://lite.duckduckgo.com/lite/"
                headers = {
                    "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                    "Content-Type": "application/x-www-form-urlencoded"
                }
                res = requests.post(url_lite, headers=headers, data={"q": query}, timeout=10)
                if res.status_code == 200:
                    matches = re.findall(r'class="result-link"\s+href="([^"]+)"[^>]*>(.*?)</a>', res.text, re.S)
                    for href, title in matches:
                        clean_title = re.sub(r'<[^>]+>', '', title).strip()
                        actual_url = href
                        if "uddg=" in href:
                            try:
                                actual_url = urllib.parse.unquote(href.split("uddg=")[1].split("&")[0])
                            except:
                                pass
                        results.append({"title": clean_title, "url": actual_url, "description": ""})
                        if len(results) >= 8:
                            break
            except Exception as e:
                print(f"[-] Error en DuckDuckGo Lite: {e}")

        # 4. Devolver resultados o Fallback Inteligente de URLs para IPTV
        if results:
            formatted_res = "Resultados de búsqueda en tiempo real:\n\n"
            for r in results:
                desc = r.get("description", "")
                desc_str = f" - {desc}" if desc else ""
                formatted_res += f"- **{r['title']}**: {r['url']}{desc_str}\n"
            return {"search_results": formatted_res}
        else:
            fallback_message = (
                "Resultados de búsqueda en tiempo real (modo de contingencia):\n\n"
                "- **IPTV-org Repositorio**: https://github.com/iptv-org/iptv (Colección principal de canales IPTV)\n"
                "- **Telechancho Infinity Repositorio**: https://github.com/telechancho/telechancho-infinity\n"
                "- **Telechancho M3U**: https://telechancho.github.io/infinity.m3u\n\n"
                "Nota: Hubo problemas para consultar los buscadores en vivo. "
                "Puedes descargar y leer directamente estas URLs utilizando la herramienta 'read_web_page'."
            )
            return {"search_results": fallback_message}

    else:
        return {"error": f"Herramienta desconocida: {name}"}


def sanitize_history(history):
    clean_history = []
    for turn in history:
        role = turn.get("role")
        parts = turn.get("parts", [])
        if not parts:
            continue
            
        if not clean_history:
            if role == "user":
                clean_history.append(turn)
            continue
            
        prev_role = clean_history[-1].get("role")
        
        if role == "user":
            if prev_role == "user":
                # Evitar duplicados consecutivos de usuario
                clean_history[-1]["parts"].extend(parts)
            elif prev_role == "model":
                clean_history.append(turn)
            elif prev_role == "function":
                # Insertar un turno de modelo de transición
                clean_history.append({
                    "role": "model",
                    "parts": [{"text": "Procesando resultados..."}]
                })
                clean_history.append(turn)
                
        elif role == "model":
            if prev_role == "user":
                clean_history.append(turn)
            elif prev_role == "function":
                clean_history.append(turn)
            elif prev_role == "model":
                clean_history[-1]["parts"].extend(parts)
                
        elif role == "function":
            if prev_role == "model":
                prev_parts = clean_history[-1].get("parts", [])
                has_fc = any("functionCall" in p for p in prev_parts)
                if has_fc:
                    clean_history.append(turn)
                    
    return clean_history

def query_gemini(chat_id, history):
    clean_history = sanitize_history(history)
    if chat_id in sessions:
        sessions[chat_id] = clean_history
        
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent?key={GEMINI_KEY}"
    payload = {
        "contents": clean_history,
        "tools": GEMINI_TOOLS,
        "systemInstruction": SYSTEM_INSTRUCTION
    }
    
    max_retries = 2
    for attempt in range(max_retries):
        try:
            res = requests.post(url, json=payload, timeout=90)
            if res.status_code == 200:
                return res.json(), None
            elif res.status_code in (503, 504, 429) and attempt < max_retries - 1:
                print(f"[!] Advertencia: API devolvió {res.status_code}. Reintentando en 5 segundos (intento {attempt+1}/{max_retries})...")
                time.sleep(5)
                continue
            else:
                err_msg = f"Error de API Gemini (status {res.status_code}): {res.text}"
                print(f"[-] {err_msg}")
                return None, err_msg
        except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
            if attempt < max_retries - 1:
                print(f"[!] Advertencia: Tiempo de espera agotado o error de conexión ({e}). Reintentando en 5 segundos...")
                time.sleep(5)
                continue
            else:
                err_msg = f"Error de conexión con la API: {str(e)}"
                print(f"[-] {err_msg}")
                return None, err_msg
        except Exception as e:
            err_msg = f"Error inesperado al consultar API: {str(e)}"
            print(f"[-] {err_msg}")
            return None, err_msg



def process_chat_message(chat_id, user_text, msg_id, image_data=None):
    print(f"\n[*] Procesando mensaje de {chat_id}: '{user_text}' (tiene imagen: {image_data is not None})")
    
    # Inicializar sesión si no existe
    if chat_id not in sessions:
        sessions[chat_id] = []
        
    session_history = sessions[chat_id]
    
    # Agregar mensaje del usuario (soportando imagen si existe)
    if image_data:
        session_history.append({
            "role": "user",
            "parts": [
                {
                    "inlineData": {
                        "mimeType": "image/jpeg",
                        "data": image_data
                    }
                },
                {"text": user_text}
            ]
        })
    else:
        session_history.append({
            "role": "user",
            "parts": [{"text": user_text}]
        })
    
    # Limitar el historial en memoria para no desbordar tokens
    if len(session_history) > 30:
        # Mantener los últimos 30 elementos
        sessions[chat_id] = session_history[-30:]
        session_history = sessions[chat_id]
        
    max_agent_loops = 40
    current_loop = 0
    
    while current_loop < max_agent_loops:
        current_loop += 1
        print(f"[*] Llamando a Gemini... (Paso {current_loop}/{max_agent_loops})")
        
        # Mostrar estado "escribiendo" en Telegram
        requests.post(f"https://api.telegram.org/bot{BOT_TOKEN}/sendChatAction", json={"chat_id": chat_id, "action": "typing"})
        
        response_json, error_details = query_gemini(chat_id, session_history)
        if not response_json or "candidates" not in response_json or not response_json["candidates"]:
            err_text = "❌ *Lo siento, ocurrió un error al comunicarme con el motor de IA.*"
            if error_details:
                try:
                    # Intentar parsear el JSON de error para mostrarlo embellecido
                    parts = error_details.split("): ", 1)
                    status_header = parts[0] + "):"
                    error_json = json.loads(parts[1])
                    formatted_json = json.dumps(error_json, indent=2, ensure_ascii=False)
                    err_text += f"\n\n*Detalles del error:*\n`{status_header}`\n```json\n{formatted_json}\n```"
                except Exception:
                    err_text += f"\n\n*Detalles del error:*\n```text\n{error_details}\n```"
            send_message(chat_id, err_text, reply_to_message_id=msg_id)
            break
            
        candidate = response_json["candidates"][0]
        content = candidate.get("content", {})
        parts = content.get("parts", [])
        
        # Guardar la respuesta del modelo en el historial
        session_history.append(content)
        
        # Buscar llamadas a funciones
        function_calls = [p["functionCall"] for p in parts if "functionCall" in p]
        
        # Si no hay llamadas a funciones, es la respuesta final de texto
        if not function_calls:
            text_response = "".join([p.get("text", "") for p in parts if "text" in p])
            if text_response:
                send_message(chat_id, text_response, reply_to_message_id=msg_id)
            else:
                send_message(chat_id, "🤖 (Respuesta vacía de la IA)", reply_to_message_id=msg_id)
            break
            
        # Si hay texto además de las llamadas a funciones, lo enviamos de inmediato
        text_message = "".join([p.get("text", "") for p in parts if "text" in p])
        if text_message:
            send_message(chat_id, text_message)
            
        # Ejecutar las funciones solicitadas
        function_response_parts = []
        for fc in function_calls:
            name = fc["name"]
            args = fc.get("args", {})
            
            # Ejecutar localmente
            result = handle_function_call(name, args, chat_id)
            
            # Agregar el resultado al formato de respuesta de función
            function_response_parts.append({
                "functionResponse": {
                    "name": name,
                    "response": result
                }
            })
            
        # Agregar las respuestas de las funciones al historial
        session_history.append({
            "role": "function",
            "parts": function_response_parts
        })
        
        # En la siguiente iteración del while, se le enviarán las respuestas de las funciones a Gemini
        # para que decida qué hacer a continuación.
        time.sleep(4)
    else:
        send_message(chat_id, "⚠️ Se alcanzó el límite máximo de pasos para esta tarea para evitar un bucle infinito.", reply_to_message_id=msg_id)

def main():
    print("==================================================")
    print("      BOT DE DESARROLLO REMOTO POR TELEGRAM       ")
    print("==================================================")
    print(f"[*] Iniciando escucha para el Chat ID: {ALLOWED_CHAT_ID}")
    print("[*] Presiona Ctrl+C para detener el bot.")
    print("==================================================")
    
    offset = 0
    
    # Obtener el último update_id para no procesar mensajes antiguos
    try:
        url = f"https://api.telegram.org/bot{BOT_TOKEN}/getUpdates"
        res = requests.get(url, params={"limit": 1, "offset": -1}, timeout=10)
        res_json = res.json()
        if res_json.get("ok") and res_json.get("result"):
            offset = res_json["result"][0]["update_id"] + 1
            print("[*] Historial de mensajes omitido.")
    except Exception as e:
        print(f"[!] Advertencia al inicializar actualizaciones: {e}")
        
    print("[*] Listo para nuevos mensajes...")
        
    while True:
        try:
            url = f"https://api.telegram.org/bot{BOT_TOKEN}/getUpdates"
            params = {"timeout": 30, "limit": 10}
            if offset:
                params["offset"] = offset
                
            res = requests.get(url, params=params, timeout=35)
            res_json = res.json()
            
            if not res_json.get("ok"):
                print(f"[-] Error de Telegram: {res_json.get('description')}")
                time.sleep(5)
                continue
                
            updates = res_json.get("result", [])
            for update in updates:
                offset = update["update_id"] + 1
                
                if "message" not in update:
                    continue
                    
                message = update["message"]
                chat = message["chat"]
                chat_id = chat["id"]
                msg_id = message["message_id"]
                
                # VALIDACIÓN DE SEGURIDAD CRÍTICA
                if chat_id != ALLOWED_CHAT_ID:
                    print(f"[!] ALERTA DE SEGURIDAD: Intento de acceso no autorizado del Chat ID {chat_id} (@{chat.get('username')})")
                    send_message(chat_id, "❌ *Acceso Denegado.* Este bot es privado y solo responde a su dueño.")
                    continue
                
                # Obtener texto o foto
                user_text = message.get("text", "")
                photo_list = message.get("photo")
                image_data = None
                
                if not user_text and not photo_list:
                    continue
                
                if photo_list:
                    photo = photo_list[-1]
                    file_id = photo.get("file_id")
                    user_text = message.get("caption", "")
                    if not user_text:
                        user_text = "Analiza esta imagen."
                    
                    try:
                        print(f"[*] Descargando foto enviada por el usuario (File ID: {file_id})...")
                        get_file_url = f"https://api.telegram.org/bot{BOT_TOKEN}/getFile"
                        res_file = requests.get(get_file_url, params={"file_id": file_id}, timeout=15)
                        file_json = res_file.json()
                        if file_json.get("ok"):
                            file_path = file_json["result"].get("file_path")
                            download_url = f"https://api.telegram.org/file/bot{BOT_TOKEN}/{file_path}"
                            
                            # Descargar la imagen
                            img_res = requests.get(download_url, timeout=30)
                            if img_res.status_code == 200:
                                image_data = base64.b64encode(img_res.content).decode("utf-8")
                                print(f"[*] Foto descargada y codificada con éxito. Tamaño b64: {len(image_data)} chars.")
                            else:
                                print(f"[-] Error de red al descargar foto (status {img_res.status_code})")
                        else:
                            print(f"[-] Error en getFile: {file_json.get('description')}")
                    except Exception as img_ex:
                        print(f"[-] Error al procesar la foto: {img_ex}")
                
                # Procesar mensaje en el hilo principal
                try:
                    process_chat_message(chat_id, user_text, msg_id, image_data=image_data)
                except Exception as ex:
                    print(f"[-] Error al procesar el mensaje: {ex}")
                    traceback.print_exc()
                    send_message(chat_id, f"❌ Ocurrió un error inesperado al procesar tu solicitud:\n`{ex}`", reply_to_message_id=msg_id)
                    
        except requests.exceptions.RequestException as re:
            # Errores de red temporales
            print(f"[-] Error de red (reconectando en 5s): {re}")
            time.sleep(5)
        except KeyboardInterrupt:
            print("\n[*] Deteniendo el bot. ¡Hasta luego!")
            break
        except Exception as e:
            print(f"[-] Error inesperado en el bucle principal: {e}")
            traceback.print_exc()
            time.sleep(5)

if __name__ == "__main__":
    main()
