<p align="center">
    <img src="https://dexterity.s3.us-east-2.amazonaws.com/v153jd/dex_banner.png" alt="Dexterity" width="75%" height="75%">
</p>

<p align="center">
    Create insanely detailed Minecraft builds in-game
</p>

<hr>

## [Download](https://www.spigotmc.org/resources/118489/) | [Wiki](https://github.com/Unfaxed/dexterity_wiki/wiki) | [Donate](https://paypal.me/c7dev) | [Java Docs](https://Unfaxed.github.io/dexterity_wiki/me/c7dev/dexterity/api/DexterityAPI.html) | [Discord](https://discord.gg/yx9hUByTzq)

![Cafe Scene](https://dexterity.s3.us-east-2.amazonaws.com/v153jd/restaurant2.png)

This scene was made entirely in-game with Dexterity. There are no resource packs, no modpacks, no custom models, and no other editing software!

<img height="20pt">

</div>

<div style="text-align: center; font-size: 18pt">

![Placing and Breaking](https://dexterity.s3.us-east-2.amazonaws.com/v153jd/placing.gif)

Place blocks in ways that would otherwise be impossible<br><br>

![Convert and Deconvert](https://dexterity.s3.us-east-2.amazonaws.com/v153jd/convert.gif)

Convert and de-convert blocks into block display entities<br><br>

![Make selections](https://dexterity.s3.us-east-2.amazonaws.com/v153jd/sel_undo.gif)

Make selections with automatic WorldEdit integration<br><br>

![Scale](https://dexterity.s3.us-east-2.amazonaws.com/v153jd/scale.gif)

Resize and skew a selection<br><br>

![Rotate](https://dexterity.s3.us-east-2.amazonaws.com/v153jd/rotation.gif)

Rotate a selection along any axis<br><br>

<hr>

Clone a selection, import schematics, run commands on click, change the glow color, consolidate entity count, and more!

<hr>

## How it works

<div style="text-align: left; font-size: 14pt">
    <p>Dexterity works by manipulating block display entities. Block displays are a type of Minecraft entity that resembles a stand-still block, except that it can be moved, rotated, and resized. Dexterity provides a tool kit that can quickly modify these entities as if they were actual blocks!</p>
    <p>There is a lot of math behind making block displays act in this way. Normally, block displays rotate around the block's corner, but by moving the actual location of the entity up by +0.5 blocks, then applying a transformation that translates the display down by -0.5 (for a 1x scale block display), it can then use yaw and pitch to rotate around the center of the block. Roll rotation is still more complicated, since this does not exist in Minecraft and thus can not be used in the same order of operations as the built-in yaw and pitch. To achieve roll, a quaternion and additional roll offset (translation) is calculated and added to the display's transformation so that the block's rotational center remains centered in the block. While yaw and pitch could also be done with a quaternion in this way, using the Minecraft yaw and pitch offers the benefit of client-side rotational interpolation for smoother animations. </p>
    <p>Math is required to calculate when a player clicks a block display since the Minecraft client does not assign a hitbox to block displays. Thus, the server must calculate it. The way this works is by calculating the intersection of a vector (the player's eye direction) and a plane (a face of a block display) defined by two orthogonal unit vectors. These two vectors lie parallel to the plane, and by solving a system of equations, the exact offset of the location the player is looking at on the block face can be calculated in the coordinate system of the rotated face. There are several faster checks, caches, and other optimizations used to minimize how many systems of equations must be solved.</p>
</div>