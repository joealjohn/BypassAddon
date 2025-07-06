# ESP Bypass Quick Setup Guide

## Quick Start (5 minutes)

### 1. Automatic Setup (Recommended)
```
1. Enable "Bypass Controller"
2. Set underground threshold to 32 (default)
3. Go underground - modules auto-activate
```

### 2. Manual Setup
```
1. Enable "Position Spoofer" (set Y-level to 15)
2. Enable "ESP Renderer" (configure block types)
3. Enable "Chunk Sniffer" (default settings)
4. Optional: Enable "Freecam Bypass" for exploration
```

## Key Settings

### Position Spoofer
- **spoof-y-level**: `15` (safe for most servers)
- **spoof-delay**: `5` ticks (increase for strict servers)
- **randomize-offset**: `enabled` (anti-detection)

### ESP Renderer  
- **render-distance**: `64` blocks (adjust for performance)
- **show-ores**: `enabled` (diamond, emerald, etc.)
- **min-y**: `-64` (scan from bedrock)
- **max-y**: `16` (focus underground)

### Chunk Sniffer
- **processing-delay**: `2` ticks (default)
- **blocks-per-tick**: `1000` (reduce if laggy)
- **chunk-radius**: `8` chunks around player

## Anti-Cheat Levels

### Low Security Servers
```
Position Spoofer: Y-level 10, delay 3 ticks
ESP Renderer: Full range, all blocks
Chunk Sniffer: Max processing speed
```

### Medium Security Servers  
```
Position Spoofer: Y-level 15, delay 5 ticks  
ESP Renderer: Ores + storage only
Chunk Sniffer: Standard processing
```

### High Security Servers
```
Position Spoofer: Y-level 20, delay 10 ticks
ESP Renderer: Ores only, conservative range
Chunk Sniffer: Slow processing, small radius
```

## Troubleshooting

### Not Finding Blocks
- Check Y-level range in ESP Renderer
- Ensure Chunk Sniffer is processing 
- Verify underground threshold in Bypass Controller

### Performance Issues
- Reduce render distance in ESP Renderer
- Lower processing rate in Chunk Sniffer
- Enable distance fading in ESP Renderer

### Detection Concerns
- Increase spoof delay in Position Spoofer
- Enable safety mode in Bypass Controller
- Use conservative Y-levels (above 15)

## Block Color Reference

- **Diamond**: Cyan
- **Emerald**: Green  
- **Ancient Debris**: Brown/Orange
- **Gold**: Yellow
- **Iron**: Gray
- **Redstone**: Red
- **Lapis**: Blue
- **Chests**: Orange
- **Spawners**: Magenta

## Commands & Hotkeys

No special commands required - all modules work through the Meteor Client GUI.

Access via: `.bind` command or Meteor Client settings panel.

## Performance Tips

1. Start with default settings
2. Gradually increase ranges if needed
3. Monitor FPS and adjust accordingly
4. Use underground mode only when necessary
5. Disable unused block types in ESP Renderer