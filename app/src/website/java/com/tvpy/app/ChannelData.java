package com.tvpy.app;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * =====================================================
 *   LISTA DE CANALES — TV PARAGUAY
 * =====================================================
 * Generado desde py.m3u — ordenado alfabéticamente
 * Duplicados ocultos (se mantiene la mejor URL)
 * =====================================================
 */
public class ChannelData {

    public static List<Channel> getChannels(android.content.Context context) {
        List<Channel> channels = new ArrayList<>();
        
        // 1. Agregar canales de Paraguay por defecto (hardcodeados)
        channels.addAll(getHardcodedChannels());
        
        // 2. Cargar canales adicionales desde assets (M3U)
        try {
            java.io.InputStream is = context.getAssets().open("default_channels.m3u");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            
            List<Channel> assetChannels = M3uParser.parse(sb.toString());
            channels.addAll(assetChannels);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return channels;
    }

    public static List<Channel> getChannels() {
        return getHardcodedChannels();
    }

    private static List<Channel> getHardcodedChannels() {
        List<Channel> channels = new ArrayList<>();


        channels.add(new Channel(
                "4DmásNoticias TV",
                "https://rds3.desdeparaguay.net/4dmasnoticiastv/4dmasnoticiastv/playlist.m3u8",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "A24 Argentina",
                "https://g1.vxral-hor.transport.edge-access.net/a15/ngrp:a24-100056_all/a24-100056.m3u8|User-Agent=Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, como Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                "📰", "Noticias", "Argentina",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "ABC-TV Paraguay",
                "dailymotion://kQRS6ZAjGuMkByE4Mtc?referer=https://www.abc.com.py/&embedder=https://www.abc.com.py/",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Alcance FM PLAY TV",
                "https://video.wilohosting.com:19360/alcancefmtv/alcancefmtv.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Almaya TV",
                "https://video.hostingcaaguazu.com:19360/almayatv/almayatv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "América TV Argentina",
                "https://prepublish.f.qaotic.net/a07/americahls-100056/playlist_720p.m3u8|User-Agent=Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, como Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                "🎬", "Entretenimiento", "Argentina",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Azteca Internacional (México)",
                "https://azt-mun.otteravision.com/azt/mun/mun.m3u8",
                "🎬", "Entretenimiento", "México",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Bruno Masi TV",
                "https://rds3.desdeparaguay.net/brunomasitv/brunomasitv/playlist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "C9N",
                "https://alba-py-c9n-c9n.stream.mediatiquestream.com/index.m3u8",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Canal 5 TV Max",
                "https://video.wilohosting.com:19360/tvmax/tvmax.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Canal 8 C&amp;C Producciones",
                "https://video.hostingcaaguazu.com:19360/canal8cvs/canal8cvs.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Canal 8 Vision TV",
                "https://tigocloudgen.desdeparaguay.net/satelitaltv/satelitaltv/playlist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Canal Pro",
                "https://www.desde-paraguay.com/pro.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Claro Sports",
                "https://dai.google.com/linear/hls/event/_OKWx76jT7mivD6d-25QAw/master.m3u8",
                "⚽", "Deporte", "Internacional",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Cosmos TV",
                "https://video.hostingcaaguazu.com:19360/cosmostv/cosmostv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Cáritas TV",
                "https://rds3.desdeparaguay.net/caritastv/caritastv/playlist.m3u8",
                "✝️", "Religioso", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Del Rey TV",
                "https://video.hostingcaaguazu.com:19360/delreytv/delreytv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Dismar Radio TV",
                "https://rds3.desdeparaguay.net/dismartv/dismartv/playlist.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "DW en Español",
                "https://dwamdstream104.akamaized.net/hls/live/2015530/dwstream104/index.m3u8",
                "📰", "Noticias", "Internacional",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "E40",
                "https://copacotf.desdeparaguay.net/e40tv/e40tv_py_alta/playlist.m3u8?admin=tvaccion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "En Lengua de Senas TV",
                "https://cloudtv.streaming.com.py/lenguasdesenas/lenguasdesenas/chunklist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Farra Play",
                "https://stream.farra.com.py/live/farra_low.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "FOX (Estados Unidos)",
                "https://aegis-cloudfront-1.tubi.video/8d9284ec-c451-4e51-a1d4-d16e5c8972af/index.m3u8",
                "🎬", "Entretenimiento", "Estados Unidos",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "FS1 (Estados Unidos)",
                "http://41.223.30.230/FOXSPORTS1/index.m3u8",
                "⚽", "Deportes", "Estados Unidos",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Gen",
                "https://copacogen.desdeparaguay.net/gentv/gentv_py_alta/playlist.m3u8?admin=nacion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "GO TV",
                "https://rds3.desdeparaguay.net/gotv/gotv/playlist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "HEi Now",
                "https://copacogen.desdeparaguay.net/heitv/heitv_py_alta/playlist.m3u8?admin=nacion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Latele",
                "https://copacotf.desdeparaguay.net/latele/latele_py_alta/playlist.m3u8?admin=tvaccion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Las Estrellas (México) [Geo-blocked]",
                "https://channel01-onlymex.akamaized.net/hls/live/2022749/event01/index.m3u8",
                "🎬", "Entretenimiento", "México",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "LIM TV",
                "https://tv.invasivamedia.com/hls/limtv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Mega TV",
                "https://www.desde-paraguay.com/mega.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "MiTV",
                "https://rds3.desdeparaguay.net/mitv/mitv/playlist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Monumental TV",
                "https://copacogen.desdeparaguay.net/monumentaltv/monumentaltv_med/playlist.m3u8?admin=tvaccion",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Nande Portetepe TV",
                "https://video.hostingcaaguazu.com:19360/nandeportetepe/nandeportetepe.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Nandejara Ne'e TV",
                "https://copacogen.desdeparaguay.net/nandejaraneetv/nandejaraneetv/playlist.m3u8?admin=tvaccion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Nanduti",
                "https://zn1tf.desdeparaguay.net/nandutitv/nandutitv_alta/playlist.m3u8",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Next HD",
                "https://live.enhdtv.com:19360/nexthd/nexthd.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "NPY",
                "https://copacogen.desdeparaguay.net/npy/npy_py_alta/playlist.m3u8?admin=tvaccion",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Oasis TV",
                "https://video.hostingcaaguazu.com:19360/oasistv/oasistv.m3u8",
                "✝️", "Religioso", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Obedira TV",
                "https://video.hostingcaaguazu.com:19360/obediratv/obediratv.m3u8",
                "✝️", "Religioso", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Occidental TV",
                "https://video.wilohosting.com:19360/occidentaltv/occidentaltv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Oviedo TV",
                "https://video.wilohosting.com:19360/oviedotv/oviedotv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Paraguay TV",
                "https://stream.paraguaytv.gov.py/memfs/bbe36fed-9b49-4d1d-adaa-4bd6d1b2e386.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Paravision",
                "http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/120.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Popu TV",
                "https://copacogen.desdeparaguay.net/universotv/universotv_py_alta/playlist.m3u8?admin=nacion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Productiva TV",
                "https://copacoradios.desdeparaguay.net/productivatv/productivatv_baja/playlist.m3u8?admin=nacion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Radio Ciudad 98.9 FM TV",
                "https://video.wilohosting.com:19360/radiociudadtv/radiociudadtv.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Radio Nacional del Paraguay",
                "https://stream.paraguaytv.gov.py/memfs/ea8a90e9-10f7-4b00-8e88-42f4a838d74b.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Radio San Cristobal 88.7 FM",
                "https://video.wilohosting.com:19360/sancristobalfm/sancristobalfm.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Radio TV El Arca del Pacto",
                "https://video.wilohosting.com:19360/radiotvelarcadelpacto/radiotvelarcadelpacto.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Rally TV (Internacional)",
                "https://rally-tv-live.akamaized.net/hls/live/2117704/RallyTV-Pri/master.m3u8",
                "🏁", "Deportes", "Internacional",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "RCC TV",
                "https://copacoradios.desdeparaguay.net/rcctv/rcctv/playlist.m3u8?admin=nacion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Red Digital",
                "https://video.hostingcaaguazu.com:19360/reddigitalsanpedro/reddigitalsanpedro.m3u8",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Red Interior TV",
                "https://video.wilohosting.com:19360/redinteriortv/redinteriortv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Regional TV Yaguaron",
                "https://video.hostingcaaguazu.com:19360/regionaltv/regionaltv.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "República TV",
                "https://rds3.desdeparaguay.net/republicatv/republicatv/playlist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "RQP Paraguay",
                "https://alba-py-rqp-rqp.stream.mediatiquestream.com/index.m3u8",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "RTVE Canal 24 Horas",
                "https://rtvelivestream.rtve.es/rtvesec/24h/24h_main_dvr.m3u8",
                "📰", "Noticias", "España",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "RTVE La 1",
                "https://rtvelivestream.rtve.es/rtvesec/la1/la1_main_dvr.m3u8",
                "🎬", "Entretenimiento", "España",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "SNT",
                "https://d2qsan2ut81n2k.cloudfront.net/live/2e1f1b6a-9d03-4194-8559-2eabe61a1555/ts:abr.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Suceso TV",
                "https://live.enhdtv.com:8081/8060/index.m3u8",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "SuperTennis (Italia)",
                "https://live-embed.supertennix.hiway.media/restreamer/supertennix_client/gpu-a-c0-16/restreamer/outgest/aa3673f1-e178-44a9-a947-ef41db73211a/manifest.m3u8",
                "🎾", "Deportes", "Italia",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "SUR TV Itapua",
                "http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/1596.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Telefe Internacional (Argentina)",
                "https://telefeviacom1.akamaized.net/hls/live/2037987/viacomINT/TOK/master.m3u8",
                "🎬", "Entretenimiento", "Argentina",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Telefuturo",
                "https://copacogen.desdeparaguay.net/telefuturo/telefuturo_py_alta/playlist.m3u8?admin=tvaccion",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Telemundo (Estados Unidos)",
                "https://nbculocallive.akamaized.net/hls/live/2037499/puertorico/stream1/master.m3u8",
                "🎬", "Entretenimiento", "Estados Unidos",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Telequince",
                "https://video.hostingcaaguazu.com:19360/telequince/telequince.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Tennis Channel (Internacional)",
                "https://cdn-uw2-prod.tsv2.amagi.tv/linear/amg01444-tennischannelth-tennischnlintl-lggb/playlist.m3u8",
                "🎾", "Deportes", "Internacional",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Tigo Sports",
                "https://live.enhdtv.com:8081/8160/index.m3u8",
                "⚽", "Deporte", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Tigo Sports+",
                "http://45.170.130.224:8000/play/a04i/index.m3u8",
                "⚽", "Deporte", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Trece",
                "dailymotion://k4nLYiNrBX8W5jDbSlM?referer=https://trece.com.py/&embedder=https://trece.com.py/",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Tropicalia 93.9 FM",
                "http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/67.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "TV Aire",
                "https://video.hostingcaaguazu.com:19360/tvairepy/tvairepy.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "TV Camara",
                "http://45.184.109.10/live/69393EAE6ADBC65A68F942022362A202/119.m3u8",
                "📰", "Noticias", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "TV Canindeyu",
                "https://video.hostingcaaguazu.com:19360/tvcanindeyu/tvcanindeyu.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "TV Globo Bahia (Brasil)",
                "http://hls1.sua.tv/live/globotvbahiafhdbr2/s.m3u8",
                "🎬", "Entretenimiento", "Brasil",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "TV Pública (Argentina)",
                "https://g5.vxral-hor.transport.edge-access.net/b16/ngrp:c7_vivo01_dai_source-20001_all/playlist.m3u8|User-Agent=Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, como Gecko) Chrome/112.0.0.0 Mobile Safari/537.36",
                "🎬", "General", "Argentina",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Unicanal",
                "dailymotion://k1mHLKycOlKgo3Db5GI?referer=https://www.unicanal.com.py/&embedder=https://www.unicanal.com.py/",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "UnionTV",
                "https://tigocloud.desdeparaguay.net/800tv/800tv/playlist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "UniRadio TV",
                "https://video.hostingcaaguazu.com:19360/uniradiotv/uniradiotv.m3u8",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Universo (Estados Unidos)",
                "http://190.11.225.124:5000/live/universo_hd/playlist.m3u8",
                "🎬", "Entretenimiento", "Estados Unidos",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Venus Media",
                "https://tigocloud.desdeparaguay.net/venusmedia/venusmedia/playlist.m3u8",
                "🎬", "Entretenimiento", "Paraguay",
                Color.parseColor("#1C2333")));

        channels.add(new Channel(
                "Villa Elisa Radio TV",
                "https://copacogen.desdeparaguay.net/villaelisatv/villaelisatv/playlist.m3u8?admin=nacion",
                "📻", "Radio TV", "Paraguay",
                Color.parseColor("#1C2333")));

        return channels;
    }
}