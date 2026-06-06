import urllib.request
import re
import json

# Buscar en la API de DuckDuckGo o HTML de DDG
# URL de búsqueda de DDG en formato HTML simple (html.duckduckgo.com/html/)
query = "Discovery Channel m3u8 latino github"
url = f"https://html.duckduckgo.com/html/?q={urllib.parse.quote(query)}"

print(f"Buscando en DDG: {url}")
try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    with urllib.request.urlopen(req, timeout=10) as response:
        html = response.read().decode('utf-8', errors='ignore')
    
    # Extraer enlaces
    links = re.findall(r'href="([^"]+)"', html)
    github_links = []
    for link in links:
        if "github.com" in link:
            # Des-ofuscar enlaces de DDG si vienen con /l/?kh=...
            if "/l/?kh=" in link or "uddg=" in link:
                match = re.search(r'uddg=([^&]+)', link)
                if match:
                    actual_url = urllib.parse.unquote(match.group(1))
                    github_links.append(actual_url)
            else:
                github_links.append(link)
                
    print("Enlaces de Github encontrados:")
    for gl in set(github_links):
        print(gl)
        
except Exception as e:
    print("Error:", e)
