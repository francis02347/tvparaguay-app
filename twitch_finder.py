import requests

# Configuración de Twitch API
CLIENT_ID = 'tu_client_id_aqui'
CLIENT_SECRET = 'tu_client_secret_aqui'

def get_access_token():
    url = 'https://id.twitch.tv/oauth2/token'
    params = {
        'client_id': CLIENT_ID,
        'client_secret': CLIENT_SECRET,
        'grant_type': 'client_credentials'
    }
    response = requests.post(url, params=params)
    return response.json()['access_token']

def find_streams(viewers_count):
    token = get_access_token()
    headers = {
        'Client-ID': CLIENT_ID,
        'Authorization': f'Bearer {token}'
    }
    
    # ID de categoría "Just Chatting" es 509658
    url = 'https://api.twitch.tv/helix/streams'
    params = {
        'game_id': '509658',
        'language': 'es',
        'first': 100
    }
    
    response = requests.get(url, headers=headers, params=params)
    streams = response.json().get('data', [])
    
    # Filtrar por número exacto de visualizaciones
    filtered = [s for s in streams if s['viewer_count'] == viewers_count]
    
    for s in filtered:
        print(f"Canal: {s['user_name']} | Visualizaciones: {s['viewer_count']} | Título: {s['title']}")

if __name__ == "__main__":
    count = int(input("Introduce el número de visualizaciones objetivo: "))
    find_streams(count)
