package me.c7dev.dexterity.displays.schematics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import me.c7dev.dexterity.Dexterity;
import me.c7dev.dexterity.displays.DexterityDisplay;
import me.c7dev.dexterity.displays.schematics.token.CharToken;
import me.c7dev.dexterity.displays.schematics.token.DoubleToken;
import me.c7dev.dexterity.displays.schematics.token.StringToken;
import me.c7dev.dexterity.displays.schematics.token.Token;
import me.c7dev.dexterity.displays.schematics.token.Token.TokenType;
import me.c7dev.dexterity.util.BinaryTag;
import me.c7dev.dexterity.util.DexBlock;
import me.c7dev.dexterity.util.DexBlockState;
import me.c7dev.dexterity.util.DexTransformation;
import me.c7dev.dexterity.util.DexUtils;
import me.c7dev.dexterity.util.DexterityException;

/**
 * Reads and pastes an existing schematic file
 */
public class Schematic {
	
	private String author = null, fileName;
	private int version = 0;
	private HuffmanTree root = new HuffmanTree(null, null);
	private List<Token> data = new ArrayList<>();
	private boolean loaded = false;
	private List<SimpleDisplayState> displays = new ArrayList<>();
	private Dexterity plugin;
	private MessageDigest sha256;
	
	private enum DecodeState {
		NOT_STARTED,
		SEEK_TAG_LENGTH,
		SEEK_TYPE, //or len digits
		GET_TAG,
		SEEK_VALUE, //until delimiter
	}
	
	public static Schematic loadSchematicByName(Dexterity plugin, String name) {
		return new Schematic(plugin, name);
	}

	@Deprecated
	public Schematic(Dexterity plugin, String file_name) {
		this.fileName = file_name;
		if (!file_name.endsWith(".dexterity")) file_name += ".dexterity";
		this.plugin = plugin;
		try {
			sha256 = MessageDigest.getInstance("SHA-256");
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		try {
			File f = new File(plugin.getDataFolder().getAbsolutePath() + "/schematics/" + file_name);
			if (f.exists()) {
				
				String[] req_sections = {"schema-version", "author", "charset", "objects", "data"};
				YamlConfiguration schem = YamlConfiguration.loadConfiguration(f);
				
				for (String req : req_sections) {
					if (!schem.contains(req)) throw new DexterityException("Schematic must include '" + req + "' section!");
				}
				
				version = schem.getInt("schema-version");
				author = schem.getString("author");
				
				String hashval = schem.getString("hash");
				if (hashval == null) throw new DexterityException("Schematic is missing hash");
				String hashread = "NaCl, why not";
				
				//validate hash
				StringBuilder hashinput = new StringBuilder("NaCl, why not");
				try {
					BufferedReader reader = new BufferedReader(new FileReader(f));
					String line = reader.readLine();
					hashread = hash(hashread);
					
					while(line != null) {
						if (line.startsWith("#") || line.startsWith("hash")) {
							line = reader.readLine();
							continue;
						}
						hashread = hash(line + hashread);
						hashinput.append(line);
						line = reader.readLine();
					}
					
					reader.close();
				} catch (Exception ex) {
					ex.printStackTrace();
					Bukkit.getLogger().severe("Could not read schematic hash!");
				}
				
				if (!hashread.equals(hashval)) throw new DexterityException("Could not load schematic: Hashes do not match");
				
				load(schem);
			} else {
				throw new DexterityException(file_name + " does not exist in schematics folder!");
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public String getAuthor() {
		return author;
	}
	
	public int getSchemaVersion() {
		return version;
	}
	
	private String hash(String s) {
		return hash(s.getBytes());
	}
	
	private String hash(byte[] data) {
		return DexUtils.bytesToHex(sha256.digest(data));
	}
	
	private HuffmanTree search(Token token, int i, HuffmanTree node) {
		HuffmanTree next;
		
		if (i >= token.getTag().length) return node;
		
		if (token.getTag().bits.get(i)) {
			next = node.getLeft(); //to left
			if (next == null) {
				next = new HuffmanTree(null, null);
				node.setLeft(next);
			}
		}
		else {
			next = node.getRight();
			if (next == null) {
				next = new HuffmanTree(null, null);
				node.setRight(next);
			}
		}
		
		return search(token, i+1, next);
	}
	
	private void addToken(Token token) {
		HuffmanTree node = search(token, 0, root);
		node.setLeaf(token);
	}
	
	private boolean bit(byte in, int index) {
		return ((in >> index) & 1) == 1;
	}
	
	public List<Token> decode(byte[] data) {
		List<Token> out = new ArrayList<>();
		if (data.length == 0) return out;

		int index = 0, data_index = 0;
		byte buffer = data[0];
		HuffmanTree curr = root;
		
		while(true) {
			
			if (curr == null) { //TODO: Add to the tree, since we are defining the tag of a new token. Need to either store length of bit tag or search for delimiter
				throw new DexterityException("Could not load schematic: Received invalid data or object codes");
			}
			
			if (curr.getLeafToken() != null) {
				out.add(curr.getLeafToken());
				if (curr.getLeafToken().getType() == TokenType.DATA_END) break; //eof
				curr = root;
				continue;
			}
			
			if (index >= 8) {
				index = 0;
				data_index++;
				if (data_index == data.length) break;
				else buffer = data[data_index];
			}
			
			boolean b = bit(buffer, 7-index);
			index++;
			if (b) curr = curr.getLeft();
			else curr = curr.getRight();
			
		}
		
		return out;
	}
	
	public void decodeObjects(byte[] data) {
		if (data.length == 0) return;

		int index = 0, data_index = 0;
		byte buffer = data[0];
		HuffmanTree curr = root;
		
		DecodeState state = DecodeState.NOT_STARTED;
		int taglen = 0, tagread_index = 0;
		TokenType type = null;
		StringBuilder val = new StringBuilder();
		BinaryTag tagread = null;
		boolean toLeft = false;
		
		while(true) { //Encoder format (v1, base 10): [<tag len>] <type> [<val chars>] [tag] <block delimiter>
			
			if (state == DecodeState.NOT_STARTED) state = DecodeState.SEEK_TAG_LENGTH; //first bit - may need to run code later
			else {
				if (state == DecodeState.GET_TAG) { //read binary tag
					if (toLeft) tagread.bits.set(tagread_index);
					tagread_index++;
					if (tagread_index == taglen) state = DecodeState.SEEK_VALUE; //read value
				} else {
					if (curr == null) throw new DexterityException("Malformed objects header: Invalid sequence");

					if (curr.getLeafToken() != null) {
						Token token = curr.getLeafToken();

						if (token.getType() == TokenType.ASCII) {
							CharToken ctk = (CharToken) token;
							if (state == DecodeState.SEEK_TAG_LENGTH || state == DecodeState.SEEK_TYPE) { //tag length int
								if (ctk.getCharValue() < 48 || ctk.getCharValue() > 57) throw new DexterityException("Malformed objects header: Expected tag length to be number");
								taglen = (10*taglen) + (ctk.getCharValue() - 48);
								state = DecodeState.SEEK_TYPE; //allow 'type' token
							}
							else if (state == DecodeState.SEEK_VALUE) {
								val.append(ctk.getCharValue());
							}
						}
						else if (token.getType() == TokenType.BLOCK_DELIMITER) {
							if (state == DecodeState.SEEK_VALUE) {
								Token t = Token.createToken(type, val.toString());
								t.setTag(tagread);
								addToken(t);
							}

							//reset state
							state = DecodeState.SEEK_TAG_LENGTH;
							taglen = tagread_index = 0;
							type = null;
							tagread = null;
							val = new StringBuilder();
						}
						else if (token.getType() == TokenType.DATA_END) return;
						else if (state == DecodeState.SEEK_TYPE) {
							type = token.getType();
							tagread = new BinaryTag(taglen);
							tagread_index = 0;
							state = DecodeState.GET_TAG;
						}
						else throw new DexterityException("Malformed objects header: Token sequencing is incorrect for object definition (" + state + ")");

						curr = root;
						continue;
					} else {
						if (toLeft) curr = curr.getLeft();
						else curr = curr.getRight();
					}
				}
			}
			
			if (index >= 8) { //next byte
				index = 0;
				data_index++;
				if (data_index == data.length) break;
				else buffer = data[data_index];
			}
			toLeft = bit(buffer, 7-index);
			index++;
		}
		
	}
	
	private void load(YamlConfiguration schem) {
		if (loaded) throw new DexterityException("This schematic is already loaded!");
		
		String[] charset = schem.getString("charset").split(";");
		int index = 0;
		boolean found_end_token = false;
		TokenType[] typevals = TokenType.values();
		
		//load charset - used to define objects
		for (String charstr : charset) {
			String[] charstr_split = charstr.split(":");
			if (charstr_split.length > 1) {
				index = Integer.parseInt(charstr_split[0]);
				charstr = charstr_split[1];
			}
			
			Token token;
			if (index <= 255) token = new CharToken((char) index);
			else {
				TokenType type = typevals[index-256];
				token = new Token(type);
				if (type == TokenType.DATA_END) found_end_token = true;
			}
			token.setTag(new BinaryTag(charstr));
			addToken(token);
			index++;
		}
		
		if (!found_end_token) throw new DexterityException("Data does not contain required tokens!");
		
		//load objects header - this uses the ascii tokens to define more tokens that each have a type and value
		String objectstr = schem.getString("objects");
		decodeObjects(Base64.getDecoder().decode(objectstr));
		
		//load blocks of schematic
		String datastr = schem.getString("data");
		data = decode(Base64.getDecoder().decode(datastr));
		
		loaded = true;
		reloadBlocks(data);
		
		//free mem
		data.clear();
		root = null;
	}
	
	private DexBlockState newState(World w) {
		return new DexBlockState(new Location(w, 0, 0, 0), null, DexTransformation.newDefaultTransformation(), null, null, 0f, null);
	}
	
	/**
	 * Interprets the token sequence to translate back into {@link DexBlock} states
	 * @param data
	 */
	public void reloadBlocks(List<Token> data) { //block data interpreter
		displays.clear();
		SimpleDisplayState workingDisplay = new SimpleDisplayState(plugin.getNextLabel(fileName));
		World world = Bukkit.getWorlds().get(0); //placeholder
		DexBlockState blockState = newState(world);
		
		for (Token t : data) {
			switch(t.getType()) {
			case DISPLAY_DELIMITER:
				workingDisplay.addBlock(blockState); //won't add if data not set
				if (workingDisplay.getBlocks().size() > 0) {
					displays.addLast(workingDisplay);
					workingDisplay = new SimpleDisplayState(plugin.getNextLabel(fileName));
					blockState = newState(world);
				}
				continue;
				
			case DATA_END:
				workingDisplay.addBlock(blockState);
				if (workingDisplay.getBlocks().size() > 0) displays.addLast(workingDisplay);
				return;
				
			case BLOCK_DELIMITER:
				workingDisplay.addBlock(blockState);
				blockState = newState(world);
				continue;
			default:
			}

			if (t instanceof DoubleToken) {
				DoubleToken dt = (DoubleToken) t;
				double val = dt.getDoubleValue();

				switch(t.getType()) {
				case DX -> blockState.getLocation().setX(val);
				case DY -> blockState.getLocation().setY(val);
				case DZ -> blockState.getLocation().setZ(val);
				case YAW -> blockState.getLocation().setYaw((float) val);
				case PITCH -> blockState.getLocation().setPitch((float) val);
				case ROLL -> blockState.setRoll((float) val);
				case SCALE_X -> {
					blockState.getTransformation().getScale().setX(val);
					blockState.getTransformation().getDisplacement().setX(-0.5*val);
				}
				case SCALE_Y -> {
					blockState.getTransformation().getScale().setY(val);
					blockState.getTransformation().getDisplacement().setY(-0.5*val);
				}
				case SCALE_Z -> {
					blockState.getTransformation().getScale().setZ(val);
					blockState.getTransformation().getDisplacement().setZ(-0.5*val);
				}
				case TRANS_X -> blockState.getTransformation().getDisplacement().setX(val);
				case TRANS_Y -> blockState.getTransformation().getDisplacement().setY(val);
				case TRANS_Z -> blockState.getTransformation().getDisplacement().setZ(val);
				case ROFFSET_X -> blockState.getTransformation().getRollOffset().setX(val);
				case ROFFSET_Y -> blockState.getTransformation().getRollOffset().setY(val);
				case ROFFSET_Z -> blockState.getTransformation().getRollOffset().setZ(val);
				case QUAT_X -> blockState.getTransformation().getLeftRotation().x = (float) val;
				case QUAT_Y -> blockState.getTransformation().getLeftRotation().y = (float) val;
				case QUAT_Z -> blockState.getTransformation().getLeftRotation().z = (float) val;
				case QUAT_W -> blockState.getTransformation().getLeftRotation().w = (float) val;
				case GLOW_ARGB -> blockState.setGlow(Color.fromARGB((int) val));
				case DISPLAY_SCALE_X -> workingDisplay.getScale().setX(val);
				case DISPLAY_SCALE_Y -> workingDisplay.getScale().setY(val);
				case DISPLAY_SCALE_Z -> workingDisplay.getScale().setZ(val);
				case DISPLAY_ROT_X1 -> workingDisplay.getRotationX().setX(val);
				case DISPLAY_ROT_X2 -> workingDisplay.getRotationX().setY(val);
				case DISPLAY_ROT_X3 -> workingDisplay.getRotationX().setZ(val);
				case DISPLAY_ROT_Y1 -> workingDisplay.getRotationY().setX(val);
				case DISPLAY_ROT_Y2 -> workingDisplay.getRotationY().setY(val);
				case DISPLAY_ROT_Y3 -> workingDisplay.getRotationY().setZ(val);
				case DISPLAY_ROT_Z1 -> workingDisplay.getRotationZ().setX(val);
				case DISPLAY_ROT_Z2 -> workingDisplay.getRotationZ().setY(val);
				case DISPLAY_ROT_Z3 -> workingDisplay.getRotationZ().setZ(val);
				default -> {}
				}
			}
			else if (t instanceof StringToken) {
				StringToken st = (StringToken) t;
				switch(t.getType()) {
				case BLOCKDATA -> blockState.setBlock(Bukkit.createBlockData(st.getStringValue()));
				case LABEL -> workingDisplay.setLabel(plugin.getNextLabel(st.getStringValue()));
				default -> {}
				}
			}
			
		}
	}
	
	/**
	 * Get the planned bounding box for the overall planned display before pasting it
	 * @return
	 */
	public BoundingBox getPlannedBoundingBox() {
		BoundingBox box = null;
		for (SimpleDisplayState d : displays) {
			if (box == null) box = d.getBoundingBox();
			else box.union(d.getBoundingBox());
		}
		return box == null ? new BoundingBox() : box;
	}
	
	/**
	 * Get the planned bounding box of the nth planned sub-display
	 * @param index
	 * @return
	 */
	public BoundingBox getPlannedBoundingBox(int index) {
		SimpleDisplayState d;
		if (index >= 0 && index < displays.size()) d = displays.get(index);
		else d = displays.get(0);
		
		return d.getBoundingBox();
	}
	
	/**
	 * Create a copy of the schematic centered at the specified location
	 * @param loc
	 */
	public DexterityDisplay paste(Location loc) {
		if (!loaded) throw new DexterityException("This schematic did not load! Cannot paste.");
		loc = loc.clone();
		DexterityDisplay d = null;
		Vector locv = loc.toVector();
		double MIN_SCALE = 0.05, MAX_SCALE = 20; //prevent funny business with /d scale -set
		
		for (SimpleDisplayState display : displays) { //spawn displays
			DexterityDisplay working_disp;
			Vector scale = new Vector(Math.clamp(display.getScale().getX(), MIN_SCALE, MAX_SCALE),
					Math.clamp(display.getScale().getY(), MIN_SCALE, MAX_SCALE),
					Math.clamp(display.getScale().getZ(), MIN_SCALE, MAX_SCALE));
			if (d == null) {
				d = new DexterityDisplay(plugin, loc, scale, plugin.getNextLabel(display.getLabel()));
				d.setBaseRotation(display.getRotationX(), display.getRotationY(), display.getRotationZ());
				working_disp = d;
			}
			else {
				working_disp = new DexterityDisplay(plugin, loc, new Vector(1, 1, 1), plugin.getNextLabel(display.getLabel()));
				d.addSubdisplay(working_disp);
			}
			
			for (DexBlockState state : display.getBlocks()) {
				state.getLocation().setWorld(loc.getWorld());
				state.setDisplay(working_disp);
				new DexBlock(state, locv);
			}
		}
		
		return d;
	}
	
}
