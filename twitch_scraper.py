import requests
from bs4 import BeautifulSoup

def find_streams_by_viewers(viewers_count):
    # Usaremos la página pública de Twitch en lugar de la API para evitar autenticación
    # Esta URL filtra por categoría Just Chatting y lenguaje español
    url = f"https://www.twitch.tv/directory/category/just-chatting?tl=es"
    
    print(f"Buscando canales con {viewers_count} espectadores...")
    
    # Nota: Twitch carga contenido dinámicamente con JS. 
    # Para scraping simple sin API, la alternativa es usar herramientas de automatización como Selenium.
    print("Debido a que Twitch utiliza JavaScript para renderizar, una petición simple no basta.")
    print("Te recomiendo usar una extensión de navegador llamada 'Tampermonkey' y crear un script de usuario.")
    print("¿Quieres que te escriba el script para Tampermonkey?")

if __name__ == "__main__":
    count = int(input("Introduce el número de visualizaciones: "))
    find_streams_by_viewers(count)
