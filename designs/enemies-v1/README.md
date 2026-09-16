# PROJECT Enigma enemy and chest sprite pack

Created with built-in imagegen and integrated into the game on 2026-09-16.

Each enemy folder contains a transparent world atlas (384 x 384, sixteen 96 x 96 cells) and battle atlas (1536 x 768, twenty-four 256 x 192 cells).
The chest folder contains an opening atlas (128 x 192, four 64 x 96 cells).

World rows: down, left, right, up; four idle frames per direction.
Battle rows: idle (4 active frames), attack (6), hurt (3), defeat (6).
Chest cells in reading order: closed, unlatch, opening, open.
Use nearest-neighbor filtering. Transparent space around each character is intentional.

The compact drone floats. Humanoids match the approved operative scale, with slightly larger Aegis/Warden armor.
These exports use the same importer and size calibration as the running game.
The game reads the original source sheets in assets/adversaries and assets/props/chest.
manifest.json records layouts and calibrated idle heights; PROMPTS.md records generation prompts.

Run PREVIEW_ENEMIES_WINDOWS.bat in the project, or launch PROJECT-Enigma.jar with --enemy-preview.
Choose an enemy or Supply Chest, select a pose, pause, or step through frames.

57 automated tests passed, including frame content, margins, alignment, and chest playback.
The packaged JAR was launched from an isolated directory and each enemy and the chest visually checked.
