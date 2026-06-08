// ==UserScript==
// @name         Twitch Just Chatting Viewer Filter
// @namespace    http://tampermonkey.net/
// @version      1.1
// @description  Filtra streams de Just Chatting en español por número de espectadores
// @author       Tu asistente
// @match        https://www.twitch.tv/directory/category/just-chatting?tl=es
// @match        https://www.twitch.tv/directory/category/just-chatting?tl=Español
// @grant        none
// ==/UserScript==

(function() {
    'use strict';

    const targetViewers = prompt("Introduce el número de espectadores exacto que buscas:");

    function filterStreams() {
        // Seleccionamos todos los artículos de los canales
        const streams = document.querySelectorAll('article');
        
        streams.forEach(stream => {
            // Buscamos el elemento que contiene el contador de visualizaciones
            const viewerText = stream.querySelector('div[data-a-target="card-viewers-count"]');
            
            if (viewerText) {
                // Obtenemos el texto y convertimos a número. Ej: "1.2K espectadores" -> 1200
                let text = viewerText.innerText.toLowerCase();
                let count = 0;
                
                if (text.includes('k')) {
                    count = parseFloat(text.replace(/[^0-9.]/g, '')) * 1000;
                } else {
                    count = parseInt(text.replace(/[^0-9]/g, ''));
                }

                if (count !== parseInt(targetViewers)) {
                    stream.style.display = 'none';
                } else {
                    stream.style.display = 'block';
                    stream.style.border = '3px solid red';
                }
            }
        });
        console.log("Filtro aplicado.");
    }

    // Ejecutar periódicamente para detectar carga infinita (scroll)
    setInterval(filterStreams, 2000);
})();
