SPOTIFY GLASS – Fabric 1.21.11 (Java 21)

BAUEN
1. Offizielle Fabric Example Mod (Branch 1.21.11) herunterladen und von dort den Ordner "gradle/" sowie
   gradlew / gradlew.bat in diesen Ordner kopieren (oder: gradle wrapper --gradle-version 9.2.1).
2. ./gradlew build  ->  build/libs/spotifyglass-1.0.0.jar in den mods-Ordner (mit Fabric API).

SPOTIFY EINRICHTEN (einmalig)
1. https://developer.spotify.com/dashboard -> Create app
2. Redirect URI:  http://127.0.0.1:8888/callback   | API: "Web API"
3. Client ID kopieren. (Im Dev-Modus unter "User Management" deine Spotify-Mail eintragen.)
4. Im Spiel K -> Reiter "Mehr" -> Client ID einfügen -> "Mit Spotify verbinden".
Hinweis: Steuerung (Pause/Lautstärke/Skip) braucht Spotify Premium und einen laufenden Player.

TASTEN (änderbar in Steuerung): K Einstellungen | P Play/Pause | Links/Rechts Song | Hoch/Runter Lautstärke
In den Einstellungen: Overlay ziehen, Mausrad = Größe, Buttons klicken, Balken ziehen = Lautstärke/Spulen.
