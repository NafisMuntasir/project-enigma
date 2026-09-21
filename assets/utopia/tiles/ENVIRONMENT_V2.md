# Environment artwork v2

Generated with the built-in image generation tool. Source PNG is kept unchanged;
UtopiaAssets slices its normalized 4x4 grid at runtime, with a one-pixel inset
preventing neighboring atlas cells bleeding into a tile under linear filtering.

Rows 1-2: light ceramic floors. Two maintenance variants are used on only 2/71 tiles.
Row 3: dark structural wall caps. Row 4: wall fascia, rotated onto exposed edges.
The map remains the same: these are presentation changes, not collision changes.
The original atlas remains available for the existing stairs and other legacy props.

## Generation prompt

Use case: stylized-concept. Asset type: production-ready top-down sci-fi dungeon texture atlas for PROJECT Enigma. Generate ONE square 1024x1024 PNG tilesheet, exact 4 columns by 4 rows of square 256px cells, NO gutters, NO outer margin, no text or labels. Orthographic straight-down view, refined hand-painted pixel-art-inspired game surfaces with crisp architectural edges. Cohesive optimistic futuristic research facility: warm pale gray ceramic walkways, slate blue-gray structural walls, restrained turquoise light accents. Not isometric. Every cell entirely filled opaque edge to edge. Row 1 (4 cells): four very subtle variations of the SAME light warm-gray ceramic floor slab, almost flat low-contrast centre, fine hairline seams only at outside perimeter, discreet corner fasteners, no inner borders, no patterns, no symbols. Row 2: left two cells quiet light-gray floor variations with a narrow flush maintenance panel or small flat vent, right two cells same floor plain. Row 3: four dark slate structural wall TOP surfaces, substantially darker than floors, finely machined anodized metal, broad calm areas with sparse narrow panel seams, no floor showing, no raised individual cubes, edges same material to allow tiling into a continuous solid mass. Row 4: four identical horizontal wall FASCIA strips spanning full width of each cell: upper 35% slate top cap, middle 50% vertical brushed graphite face with subtle recessed rectangular panel, bottom 15% narrow silver chamfer and VERY thin muted cyan light strip. Horizontal repeats must join seamlessly. All rows use consistent materials, fixed gentle upper-left ambient illumination, high quality restrained environment design. NO red or blue crosses, NO medical marks, NO arrows, NO hazard stripes, NO decorative symbols, NO text, NO logos, NO objects or characters. Main priority: readable quiet light floor versus clearly solid dark walls. This is an actual tile atlas not a presentation board or scene.

The generated output is 1254x1254; proportional slicing handles non-divisible dimensions.
