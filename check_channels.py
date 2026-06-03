import os
import re
import sys
import urllib.request
import urllib.error
import concurrent.futures
from datetime import datetime

# Rutas del proyecto
M3U_PATH = os.path.join("app", "src", "website", "assets", "default_channels.m3u")
REPORT_PATH = "reporte_canales.txt"
CLEANED_M3U_PATH = os.path.join("app", "src", "website", "assets", "default_channels.m3u")

# Configuración del verificador
TIMEOUT = 4.0  # segundos
MAX_WORKERS = 40  # Hilos concurrentes

# User-Agent estándar para evitar bloqueos
HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

def parse_m3u(file_path):
    """Parsea el M3U y extrae metadatos y URL de cada canal."""
    if not os.path.exists(file_path):
        print(f"Error: No se encontró el archivo M3U en {file_path}")
        return []

    with open(file_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    channels = []
    current_metadata = []
    
    for line in lines:
        line_str = line.strip()
        if not line_str:
            continue
        if line_str.startswith("#EXTM3U"):
            continue
        if line_str.startswith("#"):
            current_metadata.append(line_str)
        else:
            channels.append({
                "metadata": current_metadata,
                "url": line_str,
                "name": extract_name(current_metadata)
            })
            current_metadata = []
            
    return channels

def extract_name(metadata):
    """Extrae el nombre del canal a partir del metadato."""
    for line in metadata:
        if line.startswith("#EXTINF:"):
            parts = line.split(",")
            if len(parts) > 1:
                return parts[-1].strip()
    return "Canal Desconocido"

def check_url(channel):
    """Verifica si la URL responde de forma exitosa."""
    url = channel["url"]
    
    clean_url = url
    if "|" in url:
        clean_url = url.split("|")[0].strip()
        
    if not (clean_url.startswith("http://") or clean_url.startswith("https://")):
        return channel, False, f"Esquema no soportado: {clean_url}"

    try:
        req = urllib.request.Request(clean_url, headers=HEADERS, method='HEAD')
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            code = response.getcode()
            if code in [200, 201, 202, 301, 302, 307, 308]:
                return channel, True, f"OK ({code})"
            else:
                return channel, False, f"Código de estado no exitoso: {code}"
    except urllib.error.HTTPError as e:
        try:
            req_get = urllib.request.Request(clean_url, headers=HEADERS, method='GET')
            with urllib.request.urlopen(req_get, timeout=TIMEOUT) as response:
                code = response.getcode()
                if code in [200, 201, 202]:
                    return channel, True, f"OK con GET ({code})"
                else:
                    return channel, False, f"GET falló con código: {code}"
        except Exception as get_err:
            return channel, False, f"Error HTTP {e.code}: {e.reason}"
    except urllib.error.URLError as e:
        return channel, False, f"Error de red/DNS: {e.reason}"
    except Exception as e:
        return channel, False, f"Error: {str(e)}"

def safe_print(text):
    """Imprime el texto de forma segura controlando errores de encoding en consolas Windows."""
    try:
        print(text)
    except UnicodeEncodeError:
        try:
            # Reemplazar caracteres no imprimibles según el encoding de la consola
            encoding = sys.stdout.encoding or 'utf-8'
            print(text.encode(encoding, errors='replace').decode(encoding))
        except Exception:
            print(text.encode('ascii', errors='replace').decode('ascii'))

def run():
    # Intentar forzar salida utf-8 en consolas modernas
    if hasattr(sys.stdout, 'reconfigure'):
        try:
            sys.stdout.reconfigure(encoding='utf-8')
        except Exception:
            pass

    safe_print("==================================================")
    safe_print("   VERIFICADOR Y LIMPIADOR AUTOMÁTICO DE CANALES  ")
    safe_print("==================================================")
    safe_print(f"Cargando canales desde: {M3U_PATH}")
    
    channels = parse_m3u(M3U_PATH)
    total = len(channels)
    if total == 0:
        safe_print("No se encontraron canales para verificar.")
        return

    safe_print(f"Detectados {total} canales. Iniciando verificación con {MAX_WORKERS} hilos concurrentes...")
    safe_print("Esto puede tardar alrededor de 1-2 minutos. Por favor espera...\n")
    
    working_channels = []
    broken_channels = []
    
    start_time = datetime.now()
    
    with concurrent.futures.ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {executor.submit(check_url, ch): ch for ch in channels}
        
        completed = 0
        for future in concurrent.futures.as_completed(futures):
            channel, is_working, message = future.result()
            completed += 1
            
            status_symbol = "[OK]" if is_working else "[FAIL]"
            safe_print(f"[{completed}/{total}] {status_symbol} {channel['name']}: {message}")
            
            if is_working:
                working_channels.append(channel)
            else:
                broken_channels.append((channel, message))

    end_time = datetime.now()
    duration = (end_time - start_time).total_seconds()
    
    safe_print("\n========================= RESUMEN =========================")
    safe_print(f"Tiempo transcurrido: {duration:.1f} segundos")
    safe_print(f"Canales que FUNCIONAN: {len(working_channels)} ({len(working_channels)/total*100:.1f}%)")
    safe_print(f"Canales CAÍDOS: {len(broken_channels)} ({len(broken_channels)/total*100:.1f}%)")
    safe_print("===========================================================")
    
    # 1. Escribir reporte detallado de caídos
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        f.write(f"REPORTE DE VERIFICACIÓN DE CANALES - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"Total canales analizados: {total}\n")
        f.write(f"Canales funcionales: {len(working_channels)}\n")
        f.write(f"Canales caídos: {len(broken_channels)}\n")
        f.write("="*60 + "\n\n")
        f.write("LISTA DE CANALES CAÍDOS:\n")
        for idx, (ch, msg) in enumerate(broken_channels, 1):
            f.write(f"{idx}. {ch['name']}\n   URL: {ch['url']}\n   Detalle: {msg}\n\n")
            
    safe_print(f"\n[OK] Reporte detallado escrito en: '{REPORT_PATH}'")
 
    # 2. Elegir modo de limpieza
    choice = ""
    if len(sys.argv) > 1:
        if "--clean" in sys.argv:
            choice = "1"
        elif "--no-clean" in sys.argv:
            choice = "2"
 
    if not choice:
        if not sys.stdin.isatty():
            safe_print("\nModo no interactivo detectado. Por defecto se guardará la lista limpia.")
            choice = "1"
        else:
            safe_print("\n¿Deseas guardar un archivo M3U limpio eliminando automáticamente los canales caídos?")
            safe_print("1. Sí, limpiar default_channels.m3u (Recomendado)")
            safe_print("2. No, conservar el archivo original intacto")
            try:
                choice = input("Selecciona una opción (1 o 2): ").strip()
            except (KeyboardInterrupt, EOFError):
                choice = "2"
            except Exception:
                choice = "1"
 
    if choice == "1" or choice == "":
        try:
            with open(CLEANED_M3U_PATH, "w", encoding="utf-8") as f:
                f.write("#EXTM3U\n")
                for ch in working_channels:
                    for meta in ch["metadata"]:
                        f.write(f"{meta}\n")
                    f.write(f"{ch['url']}\n")
            safe_print(f"\n[SUCCESS] Archivo M3U limpio guardado en: '{CLEANED_M3U_PATH}'")
            safe_print("Los canales caídos han sido removidos con éxito.")
        except Exception as e:
            safe_print(f"Error al escribir el archivo M3U limpio: {e}")
    else:
        safe_print("\nNo se han realizado modificaciones en el archivo M3U original.")
 
if __name__ == "__main__":
    run()
