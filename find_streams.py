import urllib.request

url = "https://iptv-org.github.io/iptv/categories/documentary.m3u"
print(f"Descargando {url}...")
try:
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=10) as response:
        content = response.read().decode('utf-8')
    print("Descarga exitosa. Buscando canales...")
    
    lines = content.splitlines()
    found = []
    current_extinf = None
    for line in lines:
        if line.startswith("#EXTINF:"):
            current_extinf = line
        elif line.startswith("http") and current_extinf:
            name = ""
            parts = current_extinf.split(",")
            if len(parts) > 1:
                name = parts[-1].strip()
            
            # Busquemos canales con español o de españa/latinoamérica o nombres genéricos
            if any(term in name.lower() for term in ["discovery", "national geographic", "nat geo", "history"]):
                found.append((name, line, current_extinf))
            current_extinf = None
            
    print(f"Se encontraron {len(found)} canales potenciales:")
    for name, stream_url, extinf in found:
        print(f"Nombre: {name}")
        print(f"  URL: {stream_url}")
        print(f"  Meta: {extinf}")
        print("-" * 40)
        
except Exception as e:
    print("Error:", e)
