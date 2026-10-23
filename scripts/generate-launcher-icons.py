#!/usr/bin/env python3
"""
CycleCare Android Launcher Icon Generator
Extracts the production adaptive icon foreground, monochrome layer, legacy icons,
and Play Store icon from the master artwork and generates all required mipmap resources.
"""

import os
import sys
from PIL import Image, ImageDraw

def main():
    source_path = sys.argv[1] if len(sys.argv) > 1 else "/Users/shezad/.gemini/antigravity-ide/brain/6f13d134-692a-4f69-be87-2cf963f112fe/.user_uploaded/media_1792739761364.jpg"
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    
    if not os.path.exists(source_path):
        print(f"Error: Source image not found at {source_path}")
        sys.exit(1)
        
    print(f"Loading master icon from: {source_path}")
    im = Image.open(source_path)
    w, h = im.size
    
    # Linear background model derived from source:
    # Top-Left: #FD5F6D (warm coral-rose) -> Bottom-Right: #D9023D (deep crimson-rose)
    def get_bg(x, y):
        r = max(0.0, min(255.0, 267.7 - 0.0272 * x - 0.0320 * y))
        g = max(0.0, min(255.0, 120.4 - 0.0704 * x - 0.0870 * y))
        b = max(0.0, min(255.0, 124.3 - 0.0360 * x - 0.0433 * y))
        return r, g, b

    def make_bg(width, height):
        bg = Image.new("RGBA", (width, height))
        for y in range(height):
            for x in range(width):
                sx = x * (1024.0 / width)
                sy = y * (1024.0 / height)
                r, g, b = get_bg(sx, sy)
                bg.putpixel((x, y), (int(r), int(g), int(b), 255))
        return bg

    # 1. High-fidelity extraction of master emblem (1024x1024 space)
    print("Extracting emblem with unmixed alpha matting...")
    emblem_1024 = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            if x < 125 or x > 895 or y < 150 or y > 885:
                continue
            r, g, b = im.getpixel((x, y))
            bg_r, bg_g, bg_b = get_bg(x, y)
            
            diff_g = g - bg_g
            diff_b = b - bg_b
            range_g = 255.0 - bg_g
            range_b = 255.0 - bg_b
            
            ag = diff_g / range_g
            ab = diff_b / range_b
            a = 0.5 * ag + 0.5 * ab
            
            if a <= 0.08:
                continue
            elif a >= 0.92:
                alpha = 255
                fg_r, fg_g, fg_b = r, g, b
            else:
                t = (a - 0.08) / (0.92 - 0.08)
                alpha = int(t * 255)
                def unmix(c_orig, c_bg):
                    return int(max(0, min(255, (c_orig - (1.0 - t) * c_bg) / max(0.01, t))))
                fg_r = unmix(r, bg_r)
                fg_g = unmix(g, bg_g)
                fg_b = unmix(b, bg_b)
                
            emblem_1024.putpixel((x, y), (fg_r, fg_g, fg_b, alpha))

    cropped_emblem = emblem_1024.crop((131, 158, 890, 882))
    emb_ratio = cropped_emblem.height / cropped_emblem.width
    print(f"Emblem isolated: size={cropped_emblem.size}, ratio={emb_ratio:.4f}")

    # 2. Generator for adaptive foreground and monochrome layers
    # Canvas S x S. Safe zone: 66dp / 108dp = 61.1%. Target emblem width = S * 0.60185
    def make_adaptive_layers(canvas_size):
        target_w = int(canvas_size * 0.60185)
        target_h = int(target_w * emb_ratio)
        
        resized_emb = cropped_emblem.resize((target_w, target_h), Image.Resampling.LANCZOS)
        
        fg = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        px = (canvas_size - target_w) // 2
        py = (canvas_size - target_h) // 2
        fg.paste(resized_emb, (px, py), resized_emb)
        
        # Monochrome layer for Android 13+ Material You
        mono = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
        for y in range(canvas_size):
            for x in range(canvas_size):
                _, _, _, a = fg.getpixel((x, y))
                if a > 0:
                    mono.putpixel((x, y), (255, 255, 255, a))
                    
        return fg, mono

    # 3. Generator for legacy icons (ic_launcher.png and ic_launcher_round.png)
    def make_legacy_icons(icon_size):
        bg = make_bg(icon_size, icon_size)
        
        # Emblem scaled to ~72% of icon size
        emb_w = int(icon_size * 0.72)
        emb_h = int(emb_w * emb_ratio)
        resized_emb = cropped_emblem.resize((emb_w, emb_h), Image.Resampling.LANCZOS)
        px = (icon_size - emb_w) // 2
        py = (icon_size - emb_h) // 2
        bg.paste(resized_emb, (px, py), resized_emb)
        
        # Supersampled anti-aliased masks (4x supersampling)
        ss = 4
        ss_size = icon_size * ss
        
        # Squircle mask (radius ~22%)
        mask_sq = Image.new("L", (ss_size, ss_size), 0)
        d_sq = ImageDraw.Draw(mask_sq)
        d_sq.rounded_rectangle((0, 0, ss_size - 1, ss_size - 1), radius=int(ss_size * 0.22), fill=255)
        mask_sq = mask_sq.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
        
        icon_sq = Image.new("RGBA", (icon_size, icon_size), (0, 0, 0, 0))
        icon_sq.paste(bg, (0, 0), mask_sq)
        
        # Circular mask
        mask_circ = Image.new("L", (ss_size, ss_size), 0)
        d_circ = ImageDraw.Draw(mask_circ)
        d_circ.ellipse((0, 0, ss_size - 1, ss_size - 1), fill=255)
        mask_circ = mask_circ.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
        
        icon_circ = Image.new("RGBA", (icon_size, icon_size), (0, 0, 0, 0))
        icon_circ.paste(bg, (0, 0), mask_circ)
        
        return icon_sq, icon_circ

    # Android density configurations
    densities = {
        "mdpi": {"legacy": 48, "adaptive": 108},
        "hdpi": {"legacy": 72, "adaptive": 162},
        "xhdpi": {"legacy": 96, "adaptive": 216},
        "xxhdpi": {"legacy": 144, "adaptive": 324},
        "xxxhdpi": {"legacy": 192, "adaptive": 432},
    }

    base_res = os.path.join(repo_root, "app", "src", "main", "res")

    print("\nGenerating density-specific mipmap assets:")
    for density, sizes in densities.items():
        mipmap_dir = os.path.join(base_res, f"mipmap-{density}")
        os.makedirs(mipmap_dir, exist_ok=True)
        
        # Legacy icons
        sq, circ = make_legacy_icons(sizes["legacy"])
        sq.save(os.path.join(mipmap_dir, "ic_launcher.png"))
        circ.save(os.path.join(mipmap_dir, "ic_launcher_round.png"))
        
        # Adaptive layers
        fg, mono = make_adaptive_layers(sizes["adaptive"])
        fg.save(os.path.join(mipmap_dir, "ic_launcher_foreground.png"))
        mono.save(os.path.join(mipmap_dir, "ic_launcher_monochrome.png"))
        
        legacy_sz = sizes["legacy"]
        adapt_sz = sizes["adaptive"]
        print(f"  [+] mipmap-{density}: legacy={legacy_sz}x{legacy_sz}px, adaptive={adapt_sz}x{adapt_sz}px")

    # Save master xxxhdpi assets to drawable-nodpi
    drawable_nodpi = os.path.join(base_res, "drawable-nodpi")
    os.makedirs(drawable_nodpi, exist_ok=True)
    fg_432, mono_432 = make_adaptive_layers(432)
    fg_432.save(os.path.join(drawable_nodpi, "ic_launcher_foreground.png"))
    mono_432.save(os.path.join(drawable_nodpi, "ic_launcher_monochrome.png"))
    print("  [+] drawable-nodpi: ic_launcher_foreground.png & ic_launcher_monochrome.png (432x432px)")

    # Play Store icons
    def make_store_icon(size):
        bg = make_bg(size, size)
        emb_w = int(size * 0.72)
        emb_h = int(emb_w * emb_ratio)
        emb = cropped_emblem.resize((emb_w, emb_h), Image.Resampling.LANCZOS)
        px = (size - emb_w) // 2
        py = (size - emb_h) // 2
        bg.paste(emb, (px, py), emb)
        return bg

    out_icon_dir = os.path.join(repo_root, "output", "icon")
    play_icon_dir = os.path.join(repo_root, "play-console", "icon")
    os.makedirs(out_icon_dir, exist_ok=True)
    os.makedirs(play_icon_dir, exist_ok=True)

    store_512 = make_store_icon(512)
    store_512.save(os.path.join(out_icon_dir, "playstore-icon-512.png"))
    store_512.save(os.path.join(play_icon_dir, "ic_launcher-512.png"))

    store_1024 = make_store_icon(1024)
    store_1024.save(os.path.join(out_icon_dir, "playstore-icon-1024.png"))
    print("  [+] Google Play Store Hi-Res Icon (512x512px & 1024x1024px) generated")

    print("\nAll launcher icon assets generated successfully!")

if __name__ == "__main__":
    main()
