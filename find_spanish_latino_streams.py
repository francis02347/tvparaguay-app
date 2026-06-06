import urllib.request
import re

urls = [
    "https://raw.githubusercontent.com/jpsat/IPTV/master/m3u/Latino.m3u",
    "https://raw.githubusercontent.com/jpsat/IPTV/master/m3u/Documentales.m3u",
    "https://raw.githubusercontent.com/jpsat/IPTV/master/m3u/Espana.m3u",
    "https://raw.githubusercontent.com/lucasg7/m3u/master/channels.m3u",
    "https://raw.githubusercontent.com/lucasg7/m3u/master/Latino.m3u",
    "https://raw.githubusercontent.com/Yand95/iptv_latino/main/lista.m3u",
    "https://raw.githubusercontent.com/Yand95/iptv_latino/main/latino.m3u",
    "https://raw.githubusercontent.com/Fm93/IPTV/main/lista.m3u",
    "https://raw.githubusercontent.com/Fm93/IPTV/main/Latino.m3u",
    "https://raw.githubusercontent.com/vashg/m3u8/master/list.m3u8",
    "https://raw.githubusercontent.com/vashg/m3u8/master/list.m3u",
    "https://raw.githubusercontent.com/tomy9/iptv/main/lista.m3u8",
    "https://raw.githubusercontent.com/tomy9/iptv/main/lista.m3u",
    "https://raw.githubusercontent.com/tomy9/iptv/main/tv.m3u",
    "https://raw.githubusercontent.com/tomy9/iptv/main/tv.m3u8",
    "https://raw.githubusercontent.com/acidv/iptv/master/play.m3u",
    "https://raw.githubusercontent.com/Kof99/IPTV-Latino/master/IPTV-Latino.m3u",
    "https://raw.githubusercontent.com/Kof99/IPTV-Latino/master/IPTV-Latino.m3u8"
]

target_keywords = ["discovery", "national geographic", "nat geo", "history"]

def search_url(url):
    print(f"Buscando en: {url}")
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'})
        with urllib.request.urlopen(req, timeout=5) as r:
            text = r.read().decode('utf-8', errors='ignore')
        
        lines = text.splitlines()
        found = []
        current_extinf = ""
        for line in lines:
            line = line.strip()
            if line.startswith("#EXTINF:"):
                current_extinf = line
            elif line.startswith("http") and current_extinf:
                name = current_extinf.split(",")[-1].strip()
                if any(k in name.lower() for k in target_keywords):
                    found.append((name, line, current_extinf))
                current_extinf = ""
        return found
    except Exception as e:
        # print(f"  Error: {e}")
        return []

all_found = []
for url in urls:
    res = search_url(url)
    if res:
        print(f"  --> Encontrados {len(res)} canales!")
        all_found.extend(res)

print("\n--- RESULTADOS TOTALES ---")
seen_urls = set()
unique_results = []
for name, link, extinf in all_found:
    if link not in seen_urls:
        seen_urls.add(link)
        unique_results.append((name, link, extinf))

print(f"Se encontraron {len(unique_results)} canales únicos:")
for name, link, extinf in unique_results:
    print(f"Nombre: {name}")
    print(f"  URL: {link}")
    print(f"  Meta: {extinf}")
    print("-" * 50)
