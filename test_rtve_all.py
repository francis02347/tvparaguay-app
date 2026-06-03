import urllib.request
import re

url = "https://www.tdtchannels.com/lists/tv.m3u8"
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}

try:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=15) as response:
        content = response.read().decode('utf-8', errors='ignore')
        
    lines = content.split('\n')
    
    rtve_channels = []
    current_name = ""
    for line in lines:
        line = line.strip()
        if line.startswith("#EXTINF:"):
            name_match = re.search(r',([^,]+)$', line)
            if name_match:
                current_name = name_match.group(1).strip()
        elif line.startswith("http") and ("rtve" in line.lower() or "rtve" in current_name.lower() or "24h" in current_name.lower()):
            rtve_channels.append((current_name, line))
            
    print(f"Found {len(rtve_channels)} RTVE streams in list:")
    for name, stream in rtve_channels:
        print(f"Testing: {name}")
        print(f"  URL: {stream}")
        try:
            req_test = urllib.request.Request(stream, headers=headers)
            with urllib.request.urlopen(req_test, timeout=5) as resp:
                print(f"  Status: {resp.getcode()}")
                print(f"  Sample: {resp.read(100)[:50]}")
        except Exception as e:
            print(f"  Failed: {e}")
except Exception as e:
    print("Main Error:", e)
