import os
import re
import sys

M3U_PATH = os.path.join("app", "src", "website", "assets", "default_channels.m3u")
OUTPUT_PATH = os.path.join("app", "src", "website", "assets", "default_channels.m3u")

TARGET_NAMES = [
    # 1.jpg
    "Aire de Santa Fe",
    "Alcance FM PLAY TV",
    "Alcance TV",
    "Almaya TV",
    "AMX Noticias (720p) [Not 24/7]",
    "Andalucía Cocina",
    "Antena Uno RadioVideo",
    "Atacama Noticias (720p) [Not 24/7]",
    "ATV+",
    "Avang TV",
    "Avivamiento TV (1080p) [Not 24/7]",
    "Beats Radio",
    "beIN Sports XTRA en Espanol",
    "Bendicion Channel",
    "13 Festival",
    "24/7 Canal de Noticias",
    "4DmásNoticias TV",
    "4DmásNoticias TV (1080p) [Not 24/7]",
    "Activa TV",
    "ADN 40",
    "AE Radio TV (720p) [Not 24/7]",
    "Bethel TV",
    "Bruno Masi TV",
    "BTA TV",
    "C9N",
    "Cadena Elite (720p) [Not 24/7]",
    "Canal 15 ILCE Summa Sabres",
    "Canal 24 Horas",

    # 2.jpg
    "Canal 24 Horas Canarias",
    "Canal 26",
    "Canal 26 TV IAP",
    "Canal 5 TV Max",
    "Canal 8 C&C Producciones",
    "Canal 8 C&amp;C Producciones",
    "Canal 8 Vision TV",
    "Canal ISB (Iglesia San Bernardo)",
    "Catamarca TV",
    "CBN Espanol",
    "CEACOM TV [Not 24/7]",
    "Cetelmás TV (404p)",
    "CetelmÃ³n TV (404p)",
    "CetelmÃ³n TV",
    "Conecta2TV (720p) [Not 24/7]",
    "Canal Orbe 21",
    "Canal Pro",
    "Canal PyC",
    "Canal Sur Noticias",
    "Canal Telefamilia (360p) [Not 24/7]",
    "Cartago Medios TV (720p) [Not 24/7]",
    "Contivision",
    "Cosmos TV",
    "Cristovision",
    "Dance FM",
    "Deportes TVC",

    # 3.jpg / 4.jpg
    "Esport3 (1080p) [Not 24/7]",
    "EWTN Spain & Latin America",
    "Farra Play (720p) [Not 24/7]",
    "FIFA+ Spain",
    "FM Mundo",
    "GAMTV.cr",
    "Divinity",
    "E40",
    "Ebenezer TV",
    "EBN Televisión",
    "EBN TelevisiÃ³n",
    "El Sol Network TV",
    "En Lengua de Senas TV",
    "Enlace",
    "Gen",
    "Gol Classics",
    "HEi Now",
    "Informe TV (1080p) [Not 24/7]",
    "ITV Deportes",
    "JN19 (1080p) [Not 24/7]",
    "Juice TV (1080p) [Not 24/7]",
    "Kpop Mix",
    "KSCE Vida Cristiana",
    "La Cantina Memorias (480p) [Geo-blocked]",
    "La Hermandad Salsera (1080p) [Not 24/7]",
    "La Kalle",
    "LA MIA TV (720p) [Geo-blocked]",
    "La Voz de Maria",
    "LaLiga TV",
    "Latele",
    "Lenca Television Canal 40 (720p) [Not 24/7]",
    "Lobo TV",
    "Luz Divina TV [Not 24/7]",
    "M95 Televisión Marbella (1080p) [Not 24/7]",
    "M95 TelevisiÃ³n Marbella",
    "María Visión Mexico (360p) [Not 24/7]",
    "MarÃa VisiÃ³n Mexico",
    "Mega TV",
    "MegaBox (720p) [Not 24/7]",
    "Memorias TV Classic (480p) [Geo-blocked]",
    "Metropoli Medios TV",
    "Mision ELTV",
    "Monte Maria",
    "Monumental TV",
    "Multimedios Bajío (720p) [Not 24/7]",
    "Multimedios BajÃo",
    "Multivisión Sports (720p) [Not 24/7]",
    "MultivisiÃ³n Sports",
    "Music TV Granada (1080p) [Not 24/7]",
    "Nande Portetepe TV",
    "Nandejara Ne'e TV",
    "Nanduti",
    "NCTV",
    "Next",
    "Next HD",
    "NG Federal",
    "Norte Informativo TV",
    "Noticiero 90 Minutos",

    # 5.jpg
    "NOW 70s",
    "NOW 80s",
    "NOW 90s00s",
    "NOW Rock",
    "NPY",
    "Oasis TV",
    "Obedira TV",
    "Olam Metro TV",
    "Ovacion TV (720p) [Not 24/7]",
    "Paraguay TV",
    "Paraíso TV",
    "ParaÃso TV",
    "Peniel Kids & Young",
    "Peniel TV Biblia Abierta",
    "Plous TV",
    "Power Max Radio TV",
    "Prensa Latina TV",
    "Radio 3",
    "Radio Ancasti",
    "Radio AncÃ³n",
    "Radio Hogar",
    "Radio Javan TV",
    "Radio Nacional del Paraguay",
    "Radio Realpolitik",
    "Radio Yguazú TV",
    "Radio YguazÃº TV",
    "Radiocanal San Francisco",
    "RDN (1080p) [Not 24/7]",
    "Regional TV Yaguaron",

    # 6.jpg
    "San José TV",
    "San JosÃ© TV",
    "SNT",
    "Sol Música",
    "Sol MÃºsica",
    "Stingray Classica",
    "Stingray Greatest Holiday Hits",
    "Stingray Karaoke",
    "Stingray Naturescape",
    "Stingray Romance Latino",
    "Stingray Today's Latin Pop",
    "Stingray Urban Beat",
    "Teledeporte",
    "Telefuturo",
    "Telesur HD",
    "Televida (1080p) [Not 24/7]",
    "Tigo Sports",
    "Tigo Sports+",
    "TNO Radio",
    "Totalmusic",
    "Totalmusic 2000s",
    "Totalmusic 80s",
    "Totalmusic Concerts",
    "Totalmusic Dance",
    "Trece",
    "TSi (720p) [Not 24/7]",
    "TV Aire",
    "TV Éxitos",
    "TV Ã‰xitos",
    "TVONE Nicaragua (720p) [Not 24/7]",

    # 7.jpg
    "TyC Sports",
    "UMTV (1080p) [Not 24/7]",
    "Unicanal",
    "UnionTV",
    "V Classic TV",
    "V2BEAT (720p) [Not 24/7]",
    "Venus Media",
    "Vevo Latino",
    "Vevo Pop",
    "Video Tour Channel (480p) [Not 24/7]",
    "VM Latino (720p) [Not 24/7]",
    "VoiceOver Radio TV",
    "X Level Media"
]

def normalize(name):
    """Simplifica una cadena para comparar de forma flexible."""
    if not name:
        return ""
    # Convertir a minúsculas
    name = name.lower()
    # Eliminar acentos comunes
    replacements = {
        'á': 'a', 'é': 'e', 'í': 'i', 'ó': 'o', 'ú': 'u',
        'ã': 'a', 'õ': 'o', 'ñ': 'n'
    }
    for k, v in replacements.items():
        name = name.replace(k, v)
    # Dejar solo caracteres alfanuméricos
    name = re.sub(r'[^a-z0-9]', '', name)
    return name

def parse_m3u(file_path):
    if not os.path.exists(file_path):
        print(f"Error: No existe el archivo M3U en {file_path}")
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
    for line in metadata:
        if line.startswith("#EXTINF:"):
            parts = line.split(",")
            if len(parts) > 1:
                return parts[-1].strip()
    return ""

def run():
    print("==================================================")
    print("   FILTRANDO default_channels.m3u POR CAPTURAS    ")
    print("==================================================")

    # 1. Normalizar nombres objetivo
    normalized_targets = {normalize(t): t for t in TARGET_NAMES}
    
    # 2. Leer canales originales del M3U
    channels = parse_m3u(M3U_PATH)
    total_original = len(channels)
    print(f"Cargados {total_original} canales del M3U original.")

    matched_channels = []
    unmatched_names = set(normalized_targets.keys())

    # 3. Emparejar canales
    for ch in channels:
        ch_name = ch["name"]
        norm_ch = normalize(ch_name)
        
        matched = False
        
        # Búsqueda exacta de normalizado
        if norm_ch in normalized_targets:
            matched = True
            unmatched_names.discard(norm_ch)
        else:
            # Búsqueda difusa (si alguno es subconjunto del otro)
            for t_norm in normalized_targets:
                if t_norm in norm_ch or norm_ch in t_norm:
                    matched = True
                    unmatched_names.discard(t_norm)
                    break
        
        if matched:
            matched_channels.append(ch)

    print(f"Emparejados {len(matched_channels)} de {total_original} canales.")
    
    # Mostrar qué nombres de las capturas no se encontraron en el M3U (pueden ser los hardcodeados)
    print("\nNombres en capturas que NO se encontraron en el M3U (probablemente son canales nacionales hardcodeados):")
    for name_norm in sorted(unmatched_names):
        print(f"  - {normalized_targets[name_norm]}")

    # 4. Escribir M3U de salida filtrado
    try:
        with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
            f.write("#EXTM3U\n")
            for ch in matched_channels:
                for meta in ch["metadata"]:
                    f.write(f"{meta}\n")
                f.write(f"{ch['url']}\n")
        print(f"\n[SUCCESS] Archivo filtrado escrito en: {OUTPUT_PATH}")
        print(f"Ahora incluye únicamente los {len(matched_channels)} canales seleccionados en tus capturas.")
    except Exception as e:
        print(f"Error al escribir el archivo M3U: {e}")

if __name__ == "__main__":
    run()
