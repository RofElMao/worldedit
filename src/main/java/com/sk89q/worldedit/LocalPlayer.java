// $Id$
/*
 * WorldEdit
 * Copyright (C) 2010 sk89q <http://www.sk89q.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldedit;

import java.io.File;

import com.sk89q.worldedit.bags.BlockBag;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.blocks.BlockType;
import com.sk89q.worldedit.blocks.ItemID;
import com.sk89q.worldedit.cui.CUIEvent;
import com.sk89q.worldedit.util.TargetBlock;

/**
 *
 * @author sk89q
 */
public abstract class LocalPlayer {
    /**
     * Server.
     */
    protected ServerInterface server;

    /**
     * Construct the object.
     *
     * @param server A reference to the server this player is on
     */
    protected LocalPlayer(ServerInterface server) {
        this.server = server;
    }

    /**
     * Returns true if the player is holding a pick axe.
     *
     * @return whether a pick axe is held
     */
    public boolean isHoldingPickAxe() {
        int item = getItemInHand();
        return item == ItemID.IRON_PICK
                || item == ItemID.WOOD_PICKAXE
                || item == ItemID.STONE_PICKAXE
                || item == ItemID.DIAMOND_PICKAXE
                || item == ItemID.GOLD_PICKAXE;
    }

    /**
     * Find a position for the player to stand that is not inside a block.
     * Blocks above the player will be iteratively tested until there is
     * a series of two free blocks. The player will be teleported to
     * that free position.
     *
     * @param searchPos search position
     */
    public void findFreePosition(WorldVector searchPos) {
        LocalWorld world = searchPos.getWorld();
        int x = searchPos.getBlockX();
        int y = Math.max(0, searchPos.getBlockY());
        int origY = y;
        int z = searchPos.getBlockZ();

        byte free = 0;

        while (y <= world.getMaxY() + 2) {
            if (BlockType.canPassThrough(world.getBlock(new Vector(x, y, z)))) {
                ++free;
            } else {
                free = 0;
            }

            if (free == 2) {
                if (y - 1 != origY) {
                    final Vector pos = new Vector(x, y - 2, z);
                    final int id = world.getBlockType(pos);
                    final int data = world.getBlockData(pos);
                    setPosition(new Vector(x + 0.5, y - 2 + BlockType.centralTopLimit(id, data), z + 0.5));
                }

                return;
            }

            ++y;
        }
    }

    /**
     * Set the player on the ground.
     *
     * @param searchPos The location to start searching from
     */
    public void setOnGround(WorldVector searchPos) {
        LocalWorld world = searchPos.getWorld();
        int x = searchPos.getBlockX();
        int y = Math.max(0, searchPos.getBlockY());
        int z = searchPos.getBlockZ();

        while (y >= 0) {
            final Vector pos = new Vector(x, y, z);
            final int id = world.getBlockType(pos);
            final int data = world.getBlockData(pos);
            if (!BlockType.canPassThrough(id, data)) {
                setPosition(new Vector(x + 0.5, y + BlockType.centralTopLimit(id, data), z + 0.5));
                return;
            }

            --y;
        }
    }

    /**
     * Find a position for the player to stand that is not inside a block.
     * Blocks above the player will be iteratively tested until there is
     * a series of two free blocks. The player will be teleported to
     * that free position.
     */
    public void findFreePosition() {
        findFreePosition(getBlockIn());
    }

    /**
     * Go up one level to the next free space above.
     *
     * @return true if a spot was found
     */
    public boolean ascendLevel() {
        final WorldVector pos = getBlockIn();
        final int x = pos.getBlockX();
        int y = Math.max(0, pos.getBlockY());
        final int z = pos.getBlockZ();
        final LocalWorld world = pos.getWorld();

        byte free = 0;
        byte spots = 0;

        while (y <= world.getMaxY() + 2) {
            if (BlockType.canPassThrough(world.getBlock(new Vector(x, y, z)))) {
                ++free;
            } else {
                free = 0;
            }

            if (free == 2) {
                ++spots;
                if (spots == 2) {
                    final Vector platform = new Vector(x, y - 2, z);
                    final BaseBlock block = world.getBlock(platform);
                    final int type = block.getId();

                    // Don't get put in lava!
                    if (type == BlockID.LAVA || type == BlockID.STATIONARY_LAVA) {
                        return false;
                    }

                    setPosition(platform.add(0.5, BlockType.centralTopLimit(block), 0.5));
                    return true;
                }
            }

            ++y;
        }

        return false;
    }

    /**
     * Go up one level to the next free space above.
     *
     * @return true if a spot was found
     */
    public boolean descendLevel() {
        final WorldVector pos = getBlockIn();
        final int x = pos.getBlockX();
        int y = Math.max(0, pos.getBlockY() - 1);
        final int z = pos.getBlockZ();
        final LocalWorld world = pos.getWorld();

        byte free = 0;

        while (y >= 1) {
            if (BlockType.canPassThrough(world.getBlock(new Vector(x, y, z)))) {
                ++free;
            } else {
                free = 0;
            }

            if (free == 2) {
                // So we've found a spot, but we have to drop the player
                // lightly and also check to see if there's something to
                // stand upon
                while (y >= 0) {
                    final Vector platform = new Vector(x, y, z);
                    final BaseBlock block = world.getBlock(platform);
                    final int type = block.getId();

                    // Don't want to end up in lava
                    if (type != BlockID.AIR && type != BlockID.LAVA && type != BlockID.STATIONARY_LAVA) {
                        // Found a block!
                        setPosition(platform.add(0.5, BlockType.centralTopLimit(block), 0.5));
                        return true;
                    }

                    --y;
                }

                return false;
            }

            --y;
        }

        return false;
    }

    /**
     * Ascend to the ceiling above.
     *
     * @param clearance How many blocks to leave above the player's head
     * @return whether the player was moved
     */
    public boolean ascendToCeiling(int clearance) {
        return ascendToCeiling(clearance, true);
    }

    /**
     * Ascend to the ceiling above.
     *
     * @param clearance How many blocks to leave above the player's head
     * @param alwaysGlass Always put glass under the player
     * @return whether the player was moved
     */
    public boolean ascendToCeiling(int clearance, boolean alwaysGlass) {
        Vector pos = getBlockIn();
        int x = pos.getBlockX();
        int initialY = Math.max(0, pos.getBlockY());
        int y = Math.max(0, pos.getBlockY() + 2);
        int z = pos.getBlockZ();
        LocalWorld world = getPosition().getWorld();

        // No free space above
        if (world.getBlockType(new Vector(x, y, z)) != 0) {
            return false;
        }

        while (y <= world.getMaxY()) {
            // Found a ceiling!
            if (!BlockType.canPassThrough(world.getBlock(new Vector(x, y, z)))) {
                int platformY = Math.max(initialY, y - 3 - clearance);
                floatAt(x, platformY + 1, z, alwaysGlass);
                return true;
            }

            ++y;
        }

        return false;
    }

    /**
     * Just go up.
     *
     * @param distance How far up to teleport
     * @return whether the player was moved
     */
    public boolean ascendUpwards(int distance) {
        return ascendUpwards(distance, true);
    }

    /**
     * Just go up.
     *
     * @param distance How far up to teleport
     * @param alwaysGlass Always put glass under the player
     * @return whether the player was moved
     */
    public boolean ascendUpwards(int distance, boolean alwaysGlass) {
        final Vector pos = getBlockIn();
        final int x = pos.getBlockX();
        final int initialY = Math.max(0, pos.getBlockY());
        int y = Math.max(0, pos.getBlockY() + 1);
        final int z = pos.getBlockZ();
        final int maxY = Math.min(getWorld().getMaxY() + 1, initialY + distance);
        final LocalWorld world = getPosition().getWorld();

        while (y <= world.getMaxY() + 2) {
            if (!BlockType.canPassThrough(world.getBlock(new Vector(x, y, z)))) {
                break; // Hit something
            } else if (y > maxY + 1) {
                break;
            } else if (y == maxY + 1) {
                floatAt(x, y - 1, z, alwaysGlass);
                return true;
            }

            ++y;
        }

        return false;
    }

    /**
     * Make the player float in the given blocks.
     *
     * @param x The X coordinate of the block to float in
     * @param y The Y coordinate of the block to float in
     * @param z The Z coordinate of the block to float in
     */
    public void floatAt(int x, int y, int z, boolean alwaysGlass) {
        getPosition().getWorld().setBlockType(new Vector(x, y - 1, z), BlockID.GLASS);
        setPosition(new Vector(x + 0.5, y, z + 0.5));
    }

    /**
     * Get the point of the block that is being stood in.
     *
     * @return point
     */
    public WorldVector getBlockIn() {
        WorldVector pos = getPosition();
        return WorldVector.toBlockPoint(pos.getWorld(), pos.getX(),
                pos.getY(), pos.getZ());
    }

    /**
     * Get the point of the block that is being stood upon.
     *
     * @return point
     */
    public WorldVector getBlockOn() {
        WorldVector pos = getPosition();
        return WorldVector.toBlockPoint(pos.getWorld(), pos.getX(),
                pos.getY() - 1, pos.getZ());
    }

    /**
     * Get the point of the block being looked at. May return null.
     * Will return the farthest away air block if useLastBlock is true and no other block is found.
     *
     * @param range How far to checks for blocks
     * @param useLastBlock Try to return the last valid air block found.
     * @return point
     */
    public WorldVector getBlockTrace(int range, boolean useLastBlock) {
        TargetBlock tb = new TargetBlock(this, range, 0.2);
        return (useLastBlock ? tb.getAnyTargetBlock() : tb.getTargetBlock());
    }

    public WorldVectorFace getBlockTraceFace(int range, boolean useLastBlock) {
        TargetBlock tb = new TargetBlock(this, range, 0.2);
        return (useLastBlock ? tb.getAnyTargetBlockFace() : tb.getTargetBlockFace());
    }

    /**
     * Get the point of the block being looked at. May return null.
     *
     * @param range How far to checks for blocks
     * @return point
     */
    public WorldVector getBlockTrace(int range) {
        return getBlockTrace(range, false);
    }

    /**
     * Get the point of the block being looked at. May return null.
     *
     * @param range How far to checks for blocks
     * @return point
     */
    public WorldVector getSolidBlockTrace(int range) {
        TargetBlock tb = new TargetBlock(this, range, 0.2);
        return tb.getSolidTargetBlock();
    }

    /**
     * Get the player's cardinal direction (N, W, NW, etc.). May return null.
     *
     * @return the direction
     */
    public PlayerDirection getCardinalDirection() {
        return getCardinalDirection(0);
    }

    /**
     * Get the player's cardinal direction (N, W, NW, etc.) with an offset. May return null.
     * @param yawOffset offset that is added to the player's yaw before determining the cardinal direction
     *
     * @return the direction
     */
    public PlayerDirection getCardinalDirection(int yawOffset) {
        if (getPitch() > 67.5) {
            return PlayerDirection.DOWN;
        }
        if (getPitch() < -67.5) {
            return PlayerDirection.UP;
        }

        // From hey0's code
        double rot = (getYaw() + yawOffset) % 360; //let's use real yaw now
        if (rot < 0) {
            rot += 360.0;
        }
        return getDirection(rot);
    }

    /**
     * Returns direction according to rotation. May return null.
     *
     * @param rot yaw
     * @return the direction
     */
    private static PlayerDirection getDirection(double rot) {
        if (0 <= rot && rot < 22.5) {
            return PlayerDirection.SOUTH;
        } else if (22.5 <= rot && rot < 67.5) {
            return PlayerDirection.SOUTH_WEST;
        } else if (67.5 <= rot && rot < 112.5) {
            return PlayerDirection.WEST;
        } else if (112.5 <= rot && rot < 157.5) {
            return PlayerDirection.NORTH_WEST;
        } else if (157.5 <= rot && rot < 202.5) {
            return PlayerDirection.NORTH;
        } else if (202.5 <= rot && rot < 247.5) {
            return PlayerDirection.NORTH_EAST;
        } else if (247.5 <= rot && rot < 292.5) {
            return PlayerDirection.EAST;
        } else if (292.5 <= rot && rot < 337.5) {
            return PlayerDirection.SOUTH_EAST;
        } else if (337.5 <= rot && rot < 360.0) {
            return PlayerDirection.SOUTH;
        } else {
            return null;
        }
    }

    /**
     * Get the ID of the item that the player is holding.
     *
     * @return the item id of the item the player is holding
     */
    public abstract int getItemInHand();

    /**
     * Get the name of the player.
     *
     * @return String
     */
    public abstract String getName();

    /**
     * Get the player's position.
     *
     * @return point
     */
    public abstract WorldVector getPosition();

    /**
     * Get the player's world.
     *
     * @return point
     */
    public abstract LocalWorld getWorld();

    /**
     * Get the player's view pitch.
     *
     * @return pitch
     */
    /**
     * Get the player's view pitch.
     *
     * @return pitch
     */
    public abstract double getPitch();

    /**
     * Get the player's view yaw.
     *
     * @return yaw
     */
    /**
     * Get the player's view yaw.
     *
     * @return yaw
     */
    public abstract double getYaw();

    /**
     * Gives the player an item.
     *
     * @param type The item id of the item to be given to the player
     * @param amount How many items in the stack
     */
    public abstract void giveItem(int type, int amount);

    /**
     * Pass through the wall that you are looking at.
     *
     * @param range How far to checks for blocks
     * @return whether the player was pass through
     */
    public boolean passThroughForwardWall(int range) {
        int searchDist = 0;
        TargetBlock hitBlox = new TargetBlock(this, range, 0.2);
        LocalWorld world = getPosition().getWorld();
        BlockWorldVector block;
        boolean firstBlock = true;
        int freeToFind = 2;
        boolean inFree = false;

        while ((block = hitBlox.getNextBlock()) != null) {
            boolean free = BlockType.canPassThrough(world.getBlock(block));

            if (firstBlock) {
                firstBlock = false;

                if (!free) {
                    --freeToFind;
                    continue;
                }
            }

            ++searchDist;
            if (searchDist > 20) {
                return false;
            }

            if (inFree != free) {
                if (free) {
                    --freeToFind;
                }
            }

            if (freeToFind == 0) {
                setOnGround(block);
                return true;
            }

            inFree = free;
        }

        return false;
    }

    /**
     * Print a message.
     *
     * @param msg The message text
     */
    public abstract void printRaw(String msg);

    /**
     * Print a WorldEdit message.
     *
     * @param msg The message text
     */
    public abstract void printDebug(String msg);

    /**
     * Print a WorldEdit message.
     *
     * @param msg The message text
     */
    public abstract void print(String msg);

    /**
     * Print a WorldEdit error.
     *
     * @param msg The error message text
     */
    public abstract void printError(String msg);

    /**
     * Move the player.
     *
     * @param pos Where to move them
     * @param pitch The pitch (up/down) of the player's view
     * @param yaw The yaw (left/right) of the player's view
     */
    public abstract void setPosition(Vector pos, float pitch, float yaw);

    /**
     * Move the player.
     *
     * @param pos Where to move them
     */
    public void setPosition(Vector pos) {
        setPosition(pos, (float) getPitch(), (float) getYaw());
    }

    /**
     * Get a player's list of groups.
     *
     * @return an array containing a group name per entry
     */
    public abstract String[] getGroups();

    /**
     * Get this player's block bag.
     *
     * @return the player's block bag
     */
    public abstract BlockBag getInventoryBlockBag();

    /**
     * Checks if a player has permission.
     *
     * @param perm The permission to check
     * @return true if the player has that permission
     */
    public abstract boolean hasPermission(String perm);

    /**
     * Open a file open dialog.
     *
     * @param extensions null to allow all
     * @return the selected file or null if something went wrong
     */
    public File openFileOpenDialog(String[] extensions) {
        printError("File dialogs are not supported in your environment.");
        return null;
    }

    /**
     * Open a file save dialog.
     *
     * @param extensions null to allow all
     * @return the selected file or null if something went wrong
     */
    public File openFileSaveDialog(String[] extensions) {
        printError("File dialogs are not supported in your environment.");
        return null;
    }

    /**
     * Returns true if the player can destroy bedrock.
     *
     * @return
     */
    public boolean canDestroyBedrock() {
        return hasPermission("worldedit.override.bedrock");
    }

    /**
     * Send a CUI event.
     *
     * @param event
     */
    public void dispatchCUIEvent(CUIEvent event) {
    }

    /**
     * Send the CUI handshake.
     * @deprecated Not used anymore; The CUI begins the handshake
     */
    @Deprecated
    public void dispatchCUIHandshake() {
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LocalPlayer)) {
            return false;
        }
        LocalPlayer other2 = (LocalPlayer) other;
        return other2.getName().equals(getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    public void checkPermission(String permission) throws WorldEditPermissionException {
        if (!hasPermission(permission)) {
            throw new WorldEditPermissionException();
        }
    }

    public boolean isPlayer() {
        return true;
    }

    public boolean hasCreativeMode() {
        return false;
    }
}
