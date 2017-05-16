package marytts.vi.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import marytts.vi.util.BackSylComp;;

public class TextToPhone {

	public static final String STOP_SNDS = "p,t,k";
	public static final int MAX_CODA_LEN = 2;
	public static final int MAX_NUCL_LEN = 2;
	public static final String SPECIAL_GI_LST = "giêng,gic,gin,giêm,giêt,gi";

	public static String getTone(String word){
		
		ArrayList<String> rs = convertLettersToSounds(sliptTv(word), 8);
		
		return rs.get(rs.size() - 1);
		
	}
	
	public static Map<String, String> initL2sMaps() {
		Map<String, String> l2sMap = new HashMap<>();
		l2sMap.put("m", "m");
		l2sMap.put("n", "n");
		l2sMap.put("nh", "nj");
		l2sMap.put("ng", "N");
		l2sMap.put("ngh", "N");
		l2sMap.put("b", "b");
		l2sMap.put("p", "p");
		l2sMap.put("đ", "d");
		l2sMap.put("t", "t");
		l2sMap.put("th", "tH");
		l2sMap.put("tr", "tr");
		l2sMap.put("ch", "c");
		l2sMap.put("c", "k");
		l2sMap.put("k", "k");
		l2sMap.put("q", "k");
		l2sMap.put("v", "v");
		l2sMap.put("ph", "f");
		l2sMap.put("d", "z");
		l2sMap.put("gi", "z");
		l2sMap.put("x", "s");
		l2sMap.put("r", "zr");
		l2sMap.put("s", "sr");
		l2sMap.put("g", "G");
		l2sMap.put("gh", "G");
		l2sMap.put("kh", "x");
		l2sMap.put("h", "h");
		l2sMap.put("l", "l");
		l2sMap.put("", "Q");
		return l2sMap;
	}

	public static ArrayList<String> getInitLetters() {
		return new ArrayList<String>(initL2sMaps().keySet());
	}

	public static Map<String, String> onstL2sMaps() {
		Map<String, String> l2sMap = new HashMap<>();
		l2sMap.put("u", "w");
		l2sMap.put("o", "w");
		l2sMap.put("", "");
		return l2sMap;
	}

	public static ArrayList<String> getOnstLetters() {
		return new ArrayList<String>(onstL2sMaps().keySet());
	}

	public static Map<String, String> codaL2sMaps() {
		Map<String, String> l2sMap = new HashMap<>();
		l2sMap.put("m", "m");
		l2sMap.put("n", "n");
		l2sMap.put("ng", "N");
		l2sMap.put("nh", "N");
		l2sMap.put("p", "p");
		l2sMap.put("t", "t");
		l2sMap.put("c", "k");
		l2sMap.put("ch", "k");
		l2sMap.put("i", "ji");
		l2sMap.put("y", "ji");
		l2sMap.put("o", "wu");
		l2sMap.put("u", "wu");
		l2sMap.put("", "");
		return l2sMap;
	}

	public static ArrayList<String> getCodaLetters() {
		return new ArrayList<String>(codaL2sMaps().keySet());
	}

	public static Map<String, String> nuclL2sMaps() {
		Map<String, String> l2sMap = new HashMap<>();
		l2sMap.put("u", "u");
		l2sMap.put("ư", "W");
		l2sMap.put("ô", "o");
		l2sMap.put("ơ", "oU");
		l2sMap.put("â", "oUs");
		l2sMap.put("oo", "O");
		l2sMap.put("i", "i");
		l2sMap.put("y", "i");
		l2sMap.put("ê", "e");
		l2sMap.put("e", "E");
		l2sMap.put("ă", "as");
		l2sMap.put("iê", "ie");
		l2sMap.put("ia", "ie");
		l2sMap.put("yê", "ie");
		l2sMap.put("ya", "ie");
		l2sMap.put("uô", "uo");
		l2sMap.put("ua", "uo");
		l2sMap.put("ươ", "WoU");
		l2sMap.put("ưa", "WoU");
		l2sMap.put("o", "O,Os");
		l2sMap.put("a", "Es,a,as");
		return l2sMap;
	}

	public static ArrayList<String> getNuclLetters() {
		return new ArrayList<String>(nuclL2sMaps().keySet());
	}


	public static ArrayList<String> sliptTv(String s) {

		String[] tone = new String[] { "aAeEoOuUiIyYâÂăĂêÊôÔơƠưƯ", "àÀèÈòÒùÙìÌỳỲầẦằẰềỀồỒờỜừỪ",
				"ãÃẽẼõÕũŨĩĨỹỸẫẪẵẴễỄỗỖỡỠữỮ", "ảẢẻẺỏỎủỦỉỈỷỶẩẨẳẲểỂổỔởỞửỬ", "áÁéÉóÓúÚíÍýÝấẤắẮếẾốỐớỚứỨ",
				"ạẠẹẸọỌụỤịỊỵỴậẬặẶệỆộỘợỢựỰ" };
		
		String replace = "aAeEoOuUiIyYâÂăĂêÊôÔơƠưƯ";

		ArrayList<String> character = new ArrayList<String>();
		String toneType = "1";

		for (int i = 0; i < s.length(); i++) {
			Boolean check = false;
			for (int j = 0; j < tone.length; j++) {
				if (tone[j].indexOf(s.charAt(i)) > -1) {
					character.add(replace.charAt(tone[j].indexOf(s.charAt(i))) + "");
					if(j>=1){
						toneType = "" + (j + 1);
					}
					check = true;
					break;
				}
			}
			if (!check) {
				character.add(s.charAt(i) + "");
			}
		}
		character.add(toneType);

		return character;
	}


	private static boolean checkExistsArray(String element, ArrayList<String> list) {

		for (int i = 0; i < list.size(); i++) {
			if (element.equals(list.get(i)))
				return true;
		}
		return false;
	}

	private static BackSylComp getBackSylComp(List<String> letSeq, int maxLen, ArrayList<String> list) {

		int num = Math.min(maxLen, letSeq.size());
		String s = "";
		while (num > 0) {
			s = "";
			for (int i = letSeq.size() - num; i < letSeq.size(); i++) {
				s += letSeq.get(i);
			}
			if (checkExistsArray(s, list)) {
				return new BackSylComp(num, s);
			}
			num--;

		}
		return new BackSylComp(num, s);
	}
	
	public static String joinString(ArrayList<String> lst){
		String rs = "";
		for (String string : lst) {
			rs += string;
		}
		return rs;
	}

	public static ArrayList<String> convertLettersToSounds(ArrayList<String> lettersTone, int nTones) {

		Map<String, String> initL2sMaps = initL2sMaps();
		Map<String, String> codaL2sMaps = codaL2sMaps();
		Map<String, String> nuclL2sMaps = nuclL2sMaps();
		Map<String, String> onstL2sMaps = onstL2sMaps();

		ArrayList<String> results = new ArrayList<String>();
		ArrayList<String> letters = new ArrayList<String>();
		for (int i = 0; i < lettersTone.size() - 1; i++) {
			letters.add(lettersTone.get(i).toLowerCase());
		}

		String baseSyl = joinString(letters);
		String tone = lettersTone.get(lettersTone.size() - 1);
		int leftBound = -1;
		int rightBound = letters.size();
		String onstLet = "";
		String initLet = "";

		while (checkExistsArray(initLet + letters.get(leftBound + 1), getInitLetters())) {
			leftBound += 1;
			initLet += letters.get(leftBound);
			if (leftBound + 1 == rightBound) {
				break;
			}
		}

		String initSnd = initL2sMaps.get(initLet);
		if ("gi".equals(initLet) && (SPECIAL_GI_LST.indexOf(baseSyl) >= 0)) {
			leftBound = 0;
		}

		if ("q".equals(initLet)) {
			onstLet = "u";
			leftBound = 1;
		}

		BackSylComp codaSylComp = getBackSylComp(letters.subList(leftBound + 1, rightBound), MAX_CODA_LEN,
				getCodaLetters());

		int tmpRightBound = rightBound - codaSylComp.num;
		BackSylComp nuclSylComp = getBackSylComp(letters.subList(leftBound + 1, tmpRightBound), MAX_NUCL_LEN,
				getNuclLetters());

		boolean bValid = false;
		if ("iyou".indexOf(codaSylComp.s) > -1) {
			if (nuclSylComp.num > 0 && !baseSyl.endsWith("uy")) {
				bValid = true;
			} else {
				nuclSylComp.setS(codaSylComp.s);
				codaSylComp.setS("");
				rightBound -= codaSylComp.num;
			}
		} else {
			bValid = true;
		}

		if (bValid) {
			rightBound -= codaSylComp.num + nuclSylComp.num;
		}

		String codaSnd = codaL2sMaps.get(codaSylComp.s);
		String nuclSnd = "";

		if ("".equals(nuclSylComp.s)) {
			return results;
		}
		if ("oa".indexOf(nuclSylComp.s) == -1) {
			nuclSnd = nuclL2sMaps.get(nuclSylComp.s);
		}

		if ("o".equals(nuclSylComp.s)) {
			if (baseSyl.endsWith("ong") || baseSyl.endsWith("oc")) {
				nuclSnd = "Os";
			} else {
				nuclSnd = "O";
			}
		}

		if ("a".equals(nuclSylComp.s)) {
			if (baseSyl.endsWith("anh") || baseSyl.endsWith("ach")) {
				nuclSnd = "Es";
			} else {
				if (baseSyl.endsWith("ay") || baseSyl.endsWith("au")) {
					nuclSnd = "as";
				} else {
					nuclSnd = "a";
				}
			}
		}

		if (leftBound + 1 < rightBound - 1) {
			return results;
		} else if (leftBound + 1 == rightBound - 1) {
			if (onstLet != "") {
				return results;
			} else if (!checkExistsArray(letters.get(leftBound + 1), getOnstLetters())) {
				return results;
			} else {
				onstLet = letters.get(leftBound + 1);
			}

		}

		String onstSnd = onstL2sMaps.get(onstLet);

		if(nTones==8){
			if(codaSnd != null && STOP_SNDS.indexOf(codaSnd) >=0){
				if("5".equals(tone)){
					tone = "7";
				}
				if("6".equals(tone)) {
					tone = "8";
				}
			}
		}
		
		results.add(initSnd);
		if (onstSnd != null && onstSnd != "")
			results.add(onstSnd);
		results.add(nuclSnd);
		if (codaSnd != null && codaSnd != "")
			results.add(codaSnd);
		results.add(tone);

		return results;
	}
	
}
