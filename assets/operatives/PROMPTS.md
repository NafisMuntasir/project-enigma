# Operative animation generation prompts

Mode: built-in `image_gen` (no CLI/API fallback).

References: each role's approved image in `designs/characters-v1/` plus Sentinel's corresponding animation sheet. Character sheet generation and all artwork corrections used imagegen. Java performs the deterministic game texture import, chroma-key removal and size calibration.

## Selected outputs

- `assets/operatives/hacker/battle-keyed.png` — selected generated original `exec-04f2a361-b208-4e6a-a96e-84b84df3828b.png`.
- `assets/operatives/hacker/world-keyed.png` — selected generated original `exec-83d948eb-c9a6-4cbf-8577-fa1ab84d0921.png`.
- `assets/operatives/sniper/battle-keyed.png` — selected generated original `exec-1aef2046-1440-44a8-b1cc-6148dec6d5d3.png`.
- `assets/operatives/sniper/world-keyed.png` — selected generated original `exec-08d971d2-1fb5-4ddc-8405-4ff3168cd3e1.png`.
- `assets/operatives/enforcer/battle-keyed.png` — selected generated original `exec-522ba53d-b216-4b47-b5b7-0d3a067e0564.png`.
- `assets/operatives/enforcer/world-keyed.png` — selected generated original `exec-29886aac-14e8-4d90-b6f5-282fa5b4dfc0.png`.
- `assets/operatives/bio-medic/battle-keyed.png` — selected generated original `exec-ad571ca9-b8b9-4fca-9e76-ddca0fb50ac3.png`.
- `assets/operatives/bio-medic/world-keyed.png` — selected generated original `exec-d319a23d-a16b-4d7f-a6a2-699840da417a.png`.

## hacker / battle

Use case: stylized-concept. Asset type: production animation spritesheet for PROJECT Enigma.
Image 1 is the approved character identity and clothing reference. Image 2 is the Sentinel battle sheet: use ONLY its rendering style, precise 8-column by 6-row layout, cell spacing, compact game proportions and animation staging. Replace every Sentinel with the role from image 1. No shield or Sentinel helmet on this role.
SUBJECT: Hacker: young adult brown-skinned woman, short asymmetrical black hair with cyan streak, cyan AR glasses, cropped off-white technical jacket, graphite fitted combat suit, off-white boots, cyan wrist computer. Slim athletic build, same head proportions as Sentinel. Basic attack: quick cyan wrist pulse. Skill: Overload, types on wrist deck then projects a compact bright cyan digital burst forward.
Produce exactly 48 full-body sprites arranged on an invisible uniform 8-column by 6-row grid, canvas 1536 by 1536. Each logical cell 192x256; center the character's body at cell x=96 and place soles at cell y=232. Standing body height consistently 165 pixels in all rows, slightly stylized 5-head-tall game proportions. Characters and complete weapons/effects must remain within x=12..180, y=35..234 in their own cell. Leave generous empty gutters, especially long weapons. All battle sprites face screen RIGHT, same camera, same body scale, same costume, lighting and pixel density. Crisp illustrated pixel-game style like second image, no labels or gridlines.
Rows top to bottom, frames left to right:
0 IDLE: 4 subtle distinct breathing/weight-shift frames forming a loop; columns 4..7 repeat the neutral idle.
1 ATTACK: 6 distinct successive frames: ready, anticipation, windup, impact/fire, recoil, recovery. Columns 6..7 neutral idle.
2 SKILL: 8 distinct successive frames: ready, charge start, stronger charge, forward cast/strike, strongest compact impact, recoil, recovery, ready. Skill effects kept compact INSIDE the cell and in role's accent colour.
3 GUARD: 4 distinct frames raising arms/weapon defensively, settling into brace; columns 4..7 repeat held guard.
4 HURT: 3 distinct frames of recoil backward, flinch, recovery; columns 3..7 neutral idle. No injury gore.
5 DEFEAT: 6 distinct frames standing stagger, bent knees, kneel, falling sideways, lying on side, settled on ground. Columns 6..7 repeat final lying frame. Preserve character size; do not inflate collapsed frames to standing height.
BACKGROUND: completely uniform solid RGB(255,0,255) magenta chroma-key, no gradients, checkerboard, shadows, glow haze or floor. No magenta anywhere on character. Tight hard-edged accent effects. No text, border, UI, labels, watermark or missing cells. All 48 cells populated.

## hacker / world

Use case: stylized-concept. Asset type: production four-direction exploration spritesheet for PROJECT Enigma.
Image 1: approved character identity, face, outfit, equipment and colour reference. Image 2: Sentinel world sheet, use its exact 6-column by 4-row layout, camera angles and illustrated pixel-game rendering. Replace every Sentinel with the character of image 1. Preserve her/his identity, no Sentinel helmet or shield.
SUBJECT: Hacker: young adult brown-skinned woman, short asymmetrical black hair with cyan streak, cyan AR glasses, cropped off-white technical jacket, graphite fitted combat suit, off-white boots, cyan wrist computer. Slim athletic build, same head proportions as Sentinel. Basic attack: quick cyan wrist pulse. Skill: Overload, types on wrist deck then projects a compact bright cyan digital burst forward.
Exactly 24 sprites on an invisible uniform 6-column by 4-row grid. Canvas 1536x1536; cells256x384. Same compact game proportions as Sentinel, roughly 4.5 heads tall. Entire character height about 275px, body centered at local x128, feet local y350. Keep entire sprite, carried equipment and all limbs within x24..232 and y55..352, generous clear gutters. Consistent head size and body size in EVERY frame and direction. Carry equipment compactly; rifle pointed diagonally down or slung so it cannot cross a cell boundary.
Rows top to bottom: 0 DOWN (front view, looking toward viewer); 1 LEFT (full profile looking left); 2 RIGHT (full profile looking right); 3 UP (back of head and outfit, looking away; absolutely no visible face on back views).
Every row: columns0,1 two subtly different standing idle poses; columns2,3,4,5 one coherent four-frame WALK LOOP with left-foot-forward, passing, right-foot-forward, passing. Alternate legs and arms naturally; don't just shift whole body. No attacks, no effects, no floating panels, no extra props.
Crisp readable detailed illustrated pixel-game sprites matching Sentinel's finish. Uniform solid RGB(255,0,255) magenta background everywhere outside the sprites. No shadows, floor, gradients, checkerboard, labels, gridlines, text or watermark. No magenta in character art. All 24 cells populated.

## sniper / battle

Use case: stylized-concept. Asset type: production animation spritesheet for PROJECT Enigma.
Image 1 is the approved character identity and clothing reference. Image 2 is the Sentinel battle sheet: use ONLY its rendering style, precise 8-column by 6-row layout, cell spacing, compact game proportions and animation staging. Replace every Sentinel with the role from image 1. No shield or Sentinel helmet on this role.
SUBJECT: Sniper: young adult woman with tied black ponytail, amber monocular scope over eye, short off-white hooded cape with amber trim, graphite fitted armor, off-white shin plates and boots, long off-white and black precision rifle with amber scope. Slim athletic build. Basic attack: shoulder rifle, aim and fire brief amber muzzle flash, recoil and lower. Skill: Deadeye, crouch slightly and charge amber scope then fire a stronger compact amber shot. Keep rifle entirely inside each cell.
Produce exactly 48 full-body sprites arranged on an invisible uniform 8-column by 6-row grid, canvas 1536 by 1536. Each logical cell 192x256; center the character's body at cell x=96 and place soles at cell y=232. Standing body height consistently 165 pixels in all rows, slightly stylized 5-head-tall game proportions. Characters and complete weapons/effects must remain within x=12..180, y=35..234 in their own cell. Leave generous empty gutters, especially long weapons. All battle sprites face screen RIGHT, same camera, same body scale, same costume, lighting and pixel density. Crisp illustrated pixel-game style like second image, no labels or gridlines.
Rows top to bottom, frames left to right:
0 IDLE: 4 subtle distinct breathing/weight-shift frames forming a loop; columns 4..7 repeat the neutral idle.
1 ATTACK: 6 distinct successive frames: ready, anticipation, windup, impact/fire, recoil, recovery. Columns 6..7 neutral idle.
2 SKILL: 8 distinct successive frames: ready, charge start, stronger charge, forward cast/strike, strongest compact impact, recoil, recovery, ready. Skill effects kept compact INSIDE the cell and in role's accent colour.
3 GUARD: 4 distinct frames raising arms/weapon defensively, settling into brace; columns 4..7 repeat held guard.
4 HURT: 3 distinct frames of recoil backward, flinch, recovery; columns 3..7 neutral idle. No injury gore.
5 DEFEAT: 6 distinct frames standing stagger, bent knees, kneel, falling sideways, lying on side, settled on ground. Columns 6..7 repeat final lying frame. Preserve character size; do not inflate collapsed frames to standing height.
BACKGROUND: completely uniform solid RGB(255,0,255) magenta chroma-key, no gradients, checkerboard, shadows, glow haze or floor. No magenta anywhere on character. Tight hard-edged accent effects. No text, border, UI, labels, watermark or missing cells. All 48 cells populated.

## sniper / world

Use case: stylized-concept. Asset type: production four-direction exploration spritesheet for PROJECT Enigma.
Image 1: approved character identity, face, outfit, equipment and colour reference. Image 2: Sentinel world sheet, use its exact 6-column by 4-row layout, camera angles and illustrated pixel-game rendering. Replace every Sentinel with the character of image 1. Preserve her/his identity, no Sentinel helmet or shield.
SUBJECT: Sniper: young adult woman with tied black ponytail, amber monocular scope over eye, short off-white hooded cape with amber trim, graphite fitted armor, off-white shin plates and boots, long off-white and black precision rifle with amber scope. Slim athletic build. Basic attack: shoulder rifle, aim and fire brief amber muzzle flash, recoil and lower. Skill: Deadeye, crouch slightly and charge amber scope then fire a stronger compact amber shot. Keep rifle entirely inside each cell.
Exactly 24 sprites on an invisible uniform 6-column by 4-row grid. Canvas 1536x1536; cells256x384. Same compact game proportions as Sentinel, roughly 4.5 heads tall. Entire character height about 275px, body centered at local x128, feet local y350. Keep entire sprite, carried equipment and all limbs within x24..232 and y55..352, generous clear gutters. Consistent head size and body size in EVERY frame and direction. Carry equipment compactly; rifle pointed diagonally down or slung so it cannot cross a cell boundary.
Rows top to bottom: 0 DOWN (front view, looking toward viewer); 1 LEFT (full profile looking left); 2 RIGHT (full profile looking right); 3 UP (back of head and outfit, looking away; absolutely no visible face on back views).
Every row: columns0,1 two subtly different standing idle poses; columns2,3,4,5 one coherent four-frame WALK LOOP with left-foot-forward, passing, right-foot-forward, passing. Alternate legs and arms naturally; don't just shift whole body. No attacks, no effects, no floating panels, no extra props.
Crisp readable detailed illustrated pixel-game sprites matching Sentinel's finish. Uniform solid RGB(255,0,255) magenta background everywhere outside the sprites. No shadows, floor, gradients, checkerboard, labels, gridlines, text or watermark. No magenta in character art. All 24 cells populated.

## enforcer / battle

Use case: stylized-concept. Asset type: production animation spritesheet for PROJECT Enigma.
Image 1 is the approved character identity and clothing reference. Image 2 is the Sentinel battle sheet: use ONLY its rendering style, precise 8-column by 6-row layout, cell spacing, compact game proportions and animation staging. Replace every Sentinel with the role from image 1. No shield or Sentinel helmet on this role.
SUBJECT: Enforcer: muscular adult man with short brown hair, bare face, broad off-white ceramic chest and shoulder armor over graphite suit, enormous mechanical off-white and black piston gauntlets with orange glowing rings, armored boots. Broad powerful build but SAME head-to-foot height as Sentinel. Basic attack: wind up and punch with gauntlet, recoil. Skill: Kinetic Slam, charge orange piston gauntlets then explosive forward power punch with compact orange impact sparks.
Produce exactly 48 full-body sprites arranged on an invisible uniform 8-column by 6-row grid, canvas 1536 by 1536. Each logical cell 192x256; center the character's body at cell x=96 and place soles at cell y=232. Standing body height consistently 165 pixels in all rows, slightly stylized 5-head-tall game proportions. Characters and complete weapons/effects must remain within x=12..180, y=35..234 in their own cell. Leave generous empty gutters, especially long weapons. All battle sprites face screen RIGHT, same camera, same body scale, same costume, lighting and pixel density. Crisp illustrated pixel-game style like second image, no labels or gridlines.
Rows top to bottom, frames left to right:
0 IDLE: 4 subtle distinct breathing/weight-shift frames forming a loop; columns 4..7 repeat the neutral idle.
1 ATTACK: 6 distinct successive frames: ready, anticipation, windup, impact/fire, recoil, recovery. Columns 6..7 neutral idle.
2 SKILL: 8 distinct successive frames: ready, charge start, stronger charge, forward cast/strike, strongest compact impact, recoil, recovery, ready. Skill effects kept compact INSIDE the cell and in role's accent colour.
3 GUARD: 4 distinct frames raising arms/weapon defensively, settling into brace; columns 4..7 repeat held guard.
4 HURT: 3 distinct frames of recoil backward, flinch, recovery; columns 3..7 neutral idle. No injury gore.
5 DEFEAT: 6 distinct frames standing stagger, bent knees, kneel, falling sideways, lying on side, settled on ground. Columns 6..7 repeat final lying frame. Preserve character size; do not inflate collapsed frames to standing height.
BACKGROUND: completely uniform solid RGB(255,0,255) magenta chroma-key, no gradients, checkerboard, shadows, glow haze or floor. No magenta anywhere on character. Tight hard-edged accent effects. No text, border, UI, labels, watermark or missing cells. All 48 cells populated.

## enforcer / world

Use case: stylized-concept. Asset type: production four-direction exploration spritesheet for PROJECT Enigma.
Image 1: approved character identity, face, outfit, equipment and colour reference. Image 2: Sentinel world sheet, use its exact 6-column by 4-row layout, camera angles and illustrated pixel-game rendering. Replace every Sentinel with the character of image 1. Preserve her/his identity, no Sentinel helmet or shield.
SUBJECT: Enforcer: muscular adult man with short brown hair, bare face, broad off-white ceramic chest and shoulder armor over graphite suit, enormous mechanical off-white and black piston gauntlets with orange glowing rings, armored boots. Broad powerful build but SAME head-to-foot height as Sentinel. Basic attack: wind up and punch with gauntlet, recoil. Skill: Kinetic Slam, charge orange piston gauntlets then explosive forward power punch with compact orange impact sparks.
Exactly 24 sprites on an invisible uniform 6-column by 4-row grid. Canvas 1536x1536; cells256x384. Same compact game proportions as Sentinel, roughly 4.5 heads tall. Entire character height about 275px, body centered at local x128, feet local y350. Keep entire sprite, carried equipment and all limbs within x24..232 and y55..352, generous clear gutters. Consistent head size and body size in EVERY frame and direction. Carry equipment compactly; rifle pointed diagonally down or slung so it cannot cross a cell boundary.
Rows top to bottom: 0 DOWN (front view, looking toward viewer); 1 LEFT (full profile looking left); 2 RIGHT (full profile looking right); 3 UP (back of head and outfit, looking away; absolutely no visible face on back views).
Every row: columns0,1 two subtly different standing idle poses; columns2,3,4,5 one coherent four-frame WALK LOOP with left-foot-forward, passing, right-foot-forward, passing. Alternate legs and arms naturally; don't just shift whole body. No attacks, no effects, no floating panels, no extra props.
Crisp readable detailed illustrated pixel-game sprites matching Sentinel's finish. Uniform solid RGB(255,0,255) magenta background everywhere outside the sprites. No shadows, floor, gradients, checkerboard, labels, gridlines, text or watermark. No magenta in character art. All 24 cells populated.

## hacker / battle-padding-correction

Edit target: the attached hacker battle spritesheet. Preserve EXACTLY the character identity, all 48 poses, animation order, 8 columns by 6 rows, magenta RGB(255,0,255) background, colours and drawing style. The only change: give every sprite ample empty space in its own cell. Uniformly shrink each complete sprite INCLUDING all weapons and effects by 25 percent around its body centre, and keep it centred inside its original grid cell. Do not shrink the canvas or rearrange frames. Consistent same scale throughout. Most crucial: muzzle flashes, cyan digital particles and attack effects must remain ENTIRELY inside their originating cell with at least 15px of uninterrupted magenta between the outermost particle and either vertical cell edge. No fragment from one frame may appear in an adjacent frame. Keep the crouching and lying poses naturally shorter. Crisp edges, no blur, no shadow, no labels or grid.

## sniper / battle-padding-correction

Edit target: the attached sniper battle spritesheet. Preserve EXACTLY the character identity, all 48 poses, animation order, 8 columns by 6 rows, magenta RGB(255,0,255) background, colours and drawing style. The only change: give every sprite ample empty space in its own cell. Uniformly shrink each complete sprite INCLUDING all weapons and effects by 25 percent around its body centre, and keep it centred inside its original grid cell. Do not shrink the canvas or rearrange frames. Consistent same scale throughout. Most crucial: muzzle flashes, cyan digital particles and attack effects must remain ENTIRELY inside their originating cell with at least 15px of uninterrupted magenta between the outermost particle and either vertical cell edge. No fragment from one frame may appear in an adjacent frame. Keep the crouching and lying poses naturally shorter. Crisp edges, no blur, no shadow, no labels or grid.

## enforcer / battle-padding-correction

Edit the attached Enforcer battle animation sheet. Preserve character identity, all 48 successive poses, orange/white/graphite colours, crisp sprite rendering, 8 columns x 6 rows and uniform magenta background. Correct the overly large sprites and overflowing sparks. Shrink EVERY complete figure by 30 percent uniformly, keep all at the same body scale. Center each complete pose within its own original grid cell. ALSO reduce the orange impact burst on the forward punching pose in row 3 column 4 (one-based) to a small fist-sized burst at the knuckles, rather than the current huge explosion. All gauntlets, arms and sparks must fit COMPLETELY inside their individual cell, leaving a solid uninterrupted magenta gutter of at least 15px to each cell edge. Punch frames may use a compact stance to fit. No effects may extend into the next column. Keep the canvas and exact grid layout unchanged, no gridlines, labels, shadows or checkerboard. Do not enlarge collapsed poses.

## bio-medic / world-equipment-correction

Edit target is the attached Bio-Medic 6-column by 4-row world animation sheet. Preserve the canvas, exact 24-cell layout, all body/leg poses, character size, camera directions, identity, face, coat, teal backpack, colours and solid magenta background. Fix ONLY equipment continuity: the compact white-and-teal injector pistol must remain held visibly in the same anatomical hand throughout ALL 24 idle and walking frames. Some frames currently have an empty hand and omit the injector. Add the matching small downward-pointing injector to those hands, changing hand angle only as needed, so it no longer disappears between frames. Keep it compact within each cell, same pistol design and scale. Back views should show the carried injector at the side. No new effects, no gridlines, no labels, no shadows, no other changes.
