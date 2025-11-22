package me.c7dev.dexterity.displays.schematics.token;

import java.util.UUID;

import org.bukkit.Bukkit;

import me.c7dev.dexterity.util.BinaryTag;

/**
 * Saves a particular attribute of the DexterityDisplay
 * Can be reused in multiple DexBlocks that share the same type & value token attribute
 * Each token is then given a BinaryTag in the Huffman coding step
 */
public class Token {
	
	public enum TokenType {
		//for backwards compatability, the order is FINAL - add new tokens to bottom.
		DISPLAY_DELIMITER,
		BLOCK_DELIMITER,
		DATA_END,
		ASCII,
		BLOCKDATA,
		LABEL,
		DX, //offset from center
		DY,
		DZ,
		YAW,
		PITCH,
		ROLL,
		SCALE_X,
		SCALE_Y,
		SCALE_Z,
		TRANS_X, //if not implied by scale
		TRANS_Y,
		TRANS_Z,
		QUAT_X,
		QUAT_Y,
		QUAT_Z,
		QUAT_W,
		ROFFSET_X,
		ROFFSET_Y,
		ROFFSET_Z,
		GLOW_ARGB,
		DISPLAY_SCALE_X,
		DISPLAY_SCALE_Y,
		DISPLAY_SCALE_Z,
		DISPLAY_ROT_X1,
		DISPLAY_ROT_X2,
		DISPLAY_ROT_X3,
		DISPLAY_ROT_Y1,
		DISPLAY_ROT_Y2,
		DISPLAY_ROT_Y3,
		DISPLAY_ROT_Z1,
		DISPLAY_ROT_Z2,
		DISPLAY_ROT_Z3
		//add any new types here
	}
	
	private UUID uuid = UUID.randomUUID();
	private TokenType type;
	private BinaryTag tag;
	private int depth = 0;
	
	public Token(TokenType p) {
		type = p;
	}
	
	public static Token createToken(TokenType type, String val) {
		Token r;
		switch(type) {
		case DISPLAY_DELIMITER:
		case BLOCK_DELIMITER:
		case DATA_END: //special types
			r = new Token(type);
			break;
		
		case BLOCKDATA: //strings
			if (!val.startsWith("minecraft:")); val = "minecraft:" + val; //only for blockdata
		case LABEL:
			r = new StringToken(type, val);
			break;
		
		case ASCII: //chars
			r = new CharToken((char) Byte.parseByte(val));
			break;
			
		default: //doubles
			r = new DoubleToken(type, Double.parseDouble(val.replace(',', '.')));
		}
		return r;
	}
	
	public UUID getUniqueId() {
		return uuid;
	}
	
	public boolean equals(Object o) {
		if (o instanceof Token) {
			Token n = (Token) o;
			return n.getUniqueId().equals(uuid);
		}
		return false;
	}
	
	public int hashCode() {
		return uuid.hashCode();
	}
	
	public Object getValue() {
		return null;
	}
	
	public BinaryTag getTag() {
		return tag;
	}
	
	public TokenType getType() {
		return type;
	}
	
	public void setTag(BinaryTag s) {
		tag = s;
	}
	
	public int getDepth() {
		return depth;
	}
	
	public void setDepth(int d) {
		depth = d;
	}
	
	public String toString() {
		return type.toString();
	}
	
}
