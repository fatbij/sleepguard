import json

with open('app/src/main/res/raw/weather_animation.json', 'r') as f:
    data = json.load(f)

removed = []
kept = []
for layer in data.get('layers', []):
    name = layer.get('nm', '')
    if any(x in name.lower() for x in ['bg', 'background', 'clock', 'wave']):
        removed.append(name)
    else:
        kept.append(layer)

data['layers'] = kept

# Also remove from assets
for asset in data.get('assets', []):
    asset_layers = asset.get('layers', [])
    asset['layers'] = [l for l in asset_layers
                       if not any(x in l.get('nm','').lower()
                                  for x in ['bg', 'background', 'clock', 'wave'])]

with open('app/src/main/res/raw/weather_animation.json', 'w') as f:
    json.dump(data, f)

print("Removed layers:", removed)
print("Done.")
