# Schritt-für-Schritt: whisper-tiny-german-1224 als GGML konvertieren

## Voraussetzungen
- Python 3.9+
- Git
- ~2 GB freier Speicherplatz
- Windows: WSL2 empfohlen, oder native Python-Installation

## Schritt 1: Repositories klonen
```bash
git clone https://github.com/openai/whisper
git clone https://github.com/ggml-org/whisper.cpp
pip install transformers torch huggingface_hub
```

## Schritt 2: Neues Modell laden
```bash
huggingface-cli download primeline/whisper-tiny-german-1224 \
    --local-dir ./whisper-tiny-german-1224
```

## Schritt 3: Konvertierung HuggingFace → GGML FP16
```bash
python ./whisper.cpp/models/convert-h5-to-ggml.py \
    ./whisper-tiny-german-1224/ \
    ./whisper/ \
    ./whisper.cpp/models/
```

# Ausgabedatei: ./whisper.cpp/models/ggml-model.bin (~75 MB)

## Schritt 4: Datei umbenennen und ersetzen
```bash
# Benenne die erzeugte Datei exakt wie die bestehende .bin-Datei:
mv ./whisper.cpp/models/ggml-model.bin ggml-tiny-german.bin

# Kopiere in den Asset-Ordner des Projekts:
cp ggml-tiny-german.bin shared/src/androidMain/assets/ggml-tiny-german.bin
```

## Schritt 5: Build-Verifikation
Starte einen Debug-Build und prüfe die Logs:
- Kein "model file not found"-Fehler
- Erste Transkription läuft durch (Testphrase auf Deutsch sprechen)

```bash
./gradlew :androidApp:assembleDebug
```

## Hinweis zu Checksums
SHA256 nach Konvertierung ermitteln:
```powershell
# Windows (PowerShell):
Get-FileHash ggml-tiny-german.bin -Algorithm SHA256

# Linux/Mac:
sha256sum ggml-tiny-german.bin
```

Den ermittelten Hash zur Dokumentation festhalten (keine hardcoded Prüfsummen im Code vorhanden).
