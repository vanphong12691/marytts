package marytts.language.vi;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import marytts.datatypes.MaryData;
import marytts.datatypes.MaryDataType;
import marytts.datatypes.MaryXML;
import marytts.exceptions.MaryConfigurationException;
import marytts.modules.InternalModule;
import marytts.util.MaryRuntimeUtils;
import marytts.util.dom.MaryDomUtils;
import marytts.util.dom.NameNodeFilter;
import marytts.vi.util.NumberToCharacter;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

public class Preprocess extends InternalModule {

	// abbreviations map
	private Map<Object, Object> abbrevMap;

	// symbols map
	private static final Map<String, String> symbols;

	private static final Pattern timePattern;
	private static final Pattern durationPattern;
	private static final Pattern abbrevPattern;
	private static final Pattern acronymPattern;
	private static final Pattern realNumPattern;
	private static final Pattern numberWordPattern;
	private static final Pattern datePattern;

	private static final Pattern symbolsPattern;
	private static final Pattern URLPattern;
	private static final Pattern consonantPattern;
	private static final Pattern punctuationPattern;
	private static final Pattern hashtagPattern;
	// Regex initialization
	static {
		Pattern.compile("(\\d+)(\\.\\d+)?");
		Pattern.compile("[$£€)]");
		timePattern = Pattern.compile(
				"((0?[0-9])|(1[0-1])|(1[2-9])|(2[0-3])):([0-5][0-9])(a\\.m\\.|am|pm|p\\.m\\.|a\\.m|p\\.m)?",
				Pattern.CASE_INSENSITIVE);
		durationPattern = Pattern.compile("(\\d+):([0-5][0-9]):([0-5][0-9])(:([0-5][0-9]))?");
		abbrevPattern = Pattern.compile("[a-zA-Z]{2,}\\.");
		acronymPattern = Pattern.compile("([a-zA-Z]\\.[a-zA-Z](\\.)?)+([a-zA-Z](\\.)?)?");
		realNumPattern = Pattern.compile("(-)?([0-9][\\d.]*)?(\\,([\\d.])(%)?)?");
		numberWordPattern = Pattern.compile("([a-zA-Z]+[0-9]+|[0-9]+[a-zA-Z]+)\\w*");
		datePattern = Pattern.compile(
				"(?:(?:31(\\/|-|\\.)(?:0?[13578]|1[02]))\\1|(?:(?:29|30)(\\/|-|\\.)(?:0?[1,3-9]|1[0-2])\\2))(?:(?:1[6-9]|[2-9]\\d)?\\d{2})$|^(?:29(\\/|-|\\.)0?2\\3(?:(?:(?:1[6-9]|[2-9]\\d)?(?:0[48]|[2468][048]|[13579][26])|(?:(?:16|[2468][048]|[3579][26])00))))$|^(?:0?[1-9]|1\\d|2[0-8])(\\/|-|\\.)(?:(?:0?[1-9])|(?:1[0-2]))\\4(?:(?:1[6-9]|[2-9]\\d)?\\d{2})");
		symbolsPattern = Pattern.compile("[@%#\\/\\+=&><-]");

		consonantPattern = Pattern.compile("[b-df-hj-np-tv-z]+", Pattern.CASE_INSENSITIVE);
		punctuationPattern = Pattern.compile("\\p{Punct}");
		Pattern.compile("([0-9]+)([sS])");
		Pattern.compile(",\\.:;?'\"");
		hashtagPattern = Pattern.compile("(#)(\\w+)");
		URLPattern = Pattern.compile(
				"(https?:\\/\\/)?((www\\.)?([-a-zA-Z0-9@:%._\\\\+~#=]{2,256}\\.[a-z]{2,6}\\b([-a-zA-Z0-9@:%_\\\\+.~#?&\\/=]*)))");
	}

	// HashMap initialization
	static {

		symbols = new HashMap<String, String>();
		symbols.put("@", "a còng");
		symbols.put("#", "thăng");
		symbols.put("/", "xuyệt");
		symbols.put("%", "phần trăm");
		symbols.put("+", "cộng");
		symbols.put("-", "trừ");
		symbols.put("=", "bằng");
		symbols.put(">", "lớn hơn");
		symbols.put("<", "nhỏ hơn");
		symbols.put("&", "và");
	}

	public Preprocess() {
		super("Preprocess", MaryDataType.TOKENS, MaryDataType.WORDS, new Locale("vi"));
		try {
			this.abbrevMap = loadAbbrevMap();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public MaryData process(MaryData d) throws Exception {
		Document doc = d.getDocument();
		expand(doc);
		MaryData result = new MaryData(getOutputType(), d.getLocale());
		result.setDocument(doc);
		return result;
	}

	/***
	 * processes a document in mary xml format, from Tokens to Words which can
	 * be phonemised.
	 *
	 * @param doc
	 *            doc
	 * @throws ParseException
	 *             parse exception
	 * @throws IOException
	 *             IO Exception
	 * @throws MaryConfigurationException
	 *             mary configuration exception
	 */
	protected void expand(Document doc) throws ParseException, IOException, MaryConfigurationException {
		boolean URLFirst = false;
		boolean isURL = false;
		boolean puncSplit = false;
		boolean dashSplit = false;
		String webEmailTemp = "";
		TreeWalker tw = ((DocumentTraversal) doc).createTreeWalker(doc, NodeFilter.SHOW_ELEMENT,
				new NameNodeFilter(MaryXML.TOKEN), false);
		Element t = null;

		// loop through each node in dom tree
		while ((t = (Element) tw.nextNode()) != null) {

			if (URLFirst) {
				t = (Element) tw.previousNode();
				URLFirst = false;
			}
			// save the original token text
			String origText = MaryDomUtils.tokenText(t);

			// Token là ngày tháng
			if (MaryDomUtils.tokenText(t).matches(datePattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandDate(MaryDomUtils.tokenText(t)));
				// Token là chữ và số liền nhau
			} else if (MaryDomUtils.tokenText(t).matches(numberWordPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandWordNumber(MaryDomUtils.tokenText(t)));
				// Token là số thực
			} else if (MaryDomUtils.tokenText(t).matches(realNumPattern.pattern())
					&& !"-".equals(MaryDomUtils.tokenText(t))) {

				MaryDomUtils.setTokenText(t, NumberToCharacter.readRealNumber(MaryDomUtils.tokenText(t)));

				// Token là chữ viết tắt
			} else if (MaryDomUtils.tokenText(t).matches(acronymPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandAcronym(MaryDomUtils.tokenText(t)));
				// Chữ viết tắt có thay thế
			} else if ((MaryDomUtils.tokenText(t).matches(abbrevPattern.pattern())
					|| this.abbrevMap.containsKey(MaryDomUtils.tokenText(t).toLowerCase())) && !isURL) {
				Element testAbbNode = MaryDomUtils.getNextSiblingElement((Element) t);
				boolean nextTokenIsCapital = false;
				if (testAbbNode != null && Character.isUpperCase(MaryDomUtils.tokenText(testAbbNode).charAt(0))) {
					nextTokenIsCapital = true;
				}
				MaryDomUtils.setTokenText(t, expandAbbreviation(MaryDomUtils.tokenText(t), nextTokenIsCapital));
				// Token là thời gian rút gọn(ex 11:59pm,..)
			} else if (MaryDomUtils.tokenText(t).matches("(?i)" + timePattern.pattern())) {
				Element testTimeNode = MaryDomUtils.getNextSiblingElement((Element) t);
				boolean nextTokenIsTime = false;
				if (testTimeNode != null
						&& MaryDomUtils.tokenText(testTimeNode).matches("a\\.m\\.|AM|PM|am|pm|p\\.m\\.")) {
					nextTokenIsTime = true;
				}
				MaryDomUtils.setTokenText(t, expandTime(MaryDomUtils.tokenText(t), nextTokenIsTime));
				// Token là thời gian đầy đủ (ex 11:59:14)
			} else if (MaryDomUtils.tokenText(t).matches(durationPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandDuration(MaryDomUtils.tokenText(t)));
				// hashtags
			} else if (MaryDomUtils.tokenText(t).matches(hashtagPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandHashtag(MaryDomUtils.tokenText(t)));
				// URLs
			} else if (MaryDomUtils.tokenText(t).matches(URLPattern.pattern())) {
				// matching group 2 contains the chunk we want
				Matcher urlMatcher = URLPattern.matcher(MaryDomUtils.tokenText(t));
				urlMatcher.find();
				webEmailTemp = MaryDomUtils.tokenText(t);
				isURL = true;
				MaryDomUtils.setTokenText(t, expandURL(urlMatcher.group(2)));
				// Chuyển . thành "chấm" trong trường hợp là website
			} else if (MaryDomUtils.tokenText(t).equals(".") && isURL) {
				MaryDomUtils.setTokenText(t, "chấm");
				webEmailTemp = webEmailTemp.replaceFirst("\\.", "chấm");
				if (!webEmailTemp.contains(".")) {
					isURL = false;
				}
				// symbols
			} else if (MaryDomUtils.tokenText(t).matches(symbolsPattern.pattern())) {
				MaryDomUtils.setTokenText(t, symbols.get(MaryDomUtils.tokenText(t)));
			} else if (MaryDomUtils.tokenText(t).matches("(?i)" + consonantPattern.pattern())) {
				// first check lexicon
				if (MaryRuntimeUtils.checkLexicon("vi", MaryDomUtils.tokenText(t)).length == 0) {
					MaryDomUtils.setTokenText(t, expandConsonants(MaryDomUtils.tokenText(t)));
				}
				// a final attempt to split by punctuation
			} else if (punctuationPattern.matcher(MaryDomUtils.tokenText(t)).find()
					&& MaryDomUtils.tokenText(t).length() > 1) {
				puncSplit = true;
				String[] puncTokens = MaryDomUtils.tokenText(t).split("((?<=\\p{Punct})|(?=\\p{Punct}))");
				MaryDomUtils.setTokenText(t, Arrays.toString(puncTokens).replaceAll("[,\\]\\[]", ""));
				// FIXME: skip quotes for now as we don't have any clever
				// management of the POS for the prosodic feature
			} else if (MaryDomUtils.tokenText(t).equals("\"")) {
			} else if (MaryDomUtils.tokenText(t).matches(punctuationPattern.pattern())) {
				t.setAttribute("pos", ".");
			}
			// if token isn't ignored but there is no handling rule don't add
			// MTU
			if (!origText.equals(MaryDomUtils.tokenText(t))) {
				MaryDomUtils.encloseWithMTU(t, origText, null);
				// finally, split new expanded token separated by spaces into
				// separate tokens (also catch any leftover dashes)
				String[] newTokens = MaryDomUtils.tokenText(t).split("\\s+");
				MaryDomUtils.setTokenText(t, newTokens[0]);
				for (int i = 1; i < newTokens.length; i++) {
					MaryDomUtils.appendToken(t, newTokens[i]);
					t = MaryDomUtils.getNextSiblingElement((Element) t);
				}
				// if expanded url or punctuation go over each node, otherwise
				// let TreeWalker catch up
				if (!isURL && !puncSplit && !dashSplit) {
					tw.setCurrentNode((Node) t);
				} else {
					Node n = tw.previousNode();
					// if the first node in doc is an email or web address,
					// account for this
					if (n == null) {
						URLFirst = true;
					}
					puncSplit = false;
					dashSplit = false;
				}
			}
		}
	}

	/**
	 * Chuyển thời gian thành chữ
	 * Nhập vào 15:20:30 thì kết quả là: mười lăm giờ hai mươi phút ba mươi giây.
	 * @param duration 
	 * @return string
	 */
	protected String expandDuration(String duration) {
		Matcher durMatcher = durationPattern.matcher(duration);
		durMatcher.find();
		String hrs = NumberToCharacter.readRealNumber(durMatcher.group(1)) + " giờ ";
		String mins = NumberToCharacter.readRealNumber(durMatcher.group(2)) + " phút ";
		String secs = NumberToCharacter.readRealNumber(durMatcher.group(3)) + " giây ";
		String ms = "";
		if (durMatcher.group(4) != null) {
			ms = "và " + NumberToCharacter.readRealNumber(durMatcher.group(5)) + " mi li giây ";
		} else {
			secs = " " + secs;
		}
		return hrs + mins + secs + ms;
	}

	/**
	 * Xóa tất cả dấu chấm trong từ viết tắt.
	 * @param acronym
	 * @return
	 */
	protected String expandAcronym(String acronym) {
		return acronym.replaceAll("\\.", " ");
	}

	/***
	 * expand a URL string partially by splitting by @, / and . symbols (but
	 * retaining them)
	 *
	 * @param email
	 *            email
	 * @return Arrays.toString(tokens).replaceAll("[,\\]\\[]", "")
	 */
	protected String expandURL(String email) {
		String[] tokens = email.split("((?<=[\\.@\\/])|(?=[\\.@\\/]))");
		for (int i = 0; i < tokens.length; i++) {
			tokens[i] = expandAbbreviation(tokens[i], false);
		}
		return Arrays.toString(tokens).replaceAll("[,\\]\\[]", "");
	}

	/***
	 * add a space between each char of a string
	 *
	 * @param consonants
	 *            consonants
	 * @return Joiner.on(" ").join(Lists.charactersOf(consonants))
	 */
	protected String expandConsonants(String consonants) {
		return Joiner.on(" ").join(Lists.charactersOf(consonants));
	}

	/**
	 * Xử lý cho trường hợp hastag, đang sử dụng như tiếng anh
	 * @param hashtag
	 * @return
	 */
	protected String expandHashtag(String hashtag) {
		String tag = "";
		String expandedTag = "";
		Matcher hashTagMatcher = hashtagPattern.matcher(hashtag);
		hashTagMatcher.find();
		tag = hashTagMatcher.group(2);
		if (!tag.matches("[a-z]+") || !tag.matches("[A-Z]+")) {
			String temp = "";
			for (char c : tag.toCharArray()) {
				if (Character.isDigit(c) && temp.matches("^$|[0-9]+")) {
					temp += " " + NumberToCharacter.readRealNumber(c + "") + " ";
				} else if (Character.isDigit(c) && temp.matches(".+[0-9]")) {
					temp += " " + NumberToCharacter.readRealNumber(c + "") + " ";
				} else if (Character.isDigit(c)) {
					temp += " " + NumberToCharacter.readRealNumber(c + "") + " ";
				} else if (!temp.equals("") && Character.isUpperCase(c)) {
					if (Character.isUpperCase(temp.charAt(temp.length() - 1))) {
						temp += c;
					} else {
						temp += " " + c;
					}
				} else if (Character.isAlphabetic(c) && temp.length() > 0) {
					if (Character.isDigit(temp.charAt(temp.length() - 1))) {
						temp += " " + c;
					} else {
						temp += c;
					}
				} else {
					temp += c;
				}
			}
			expandedTag = temp;
		} else {
			expandedTag = tag;
		}
		return symbols.get(hashTagMatcher.group(1)) + " " + expandedTag;
	}

	/***
	 * Thay thế những từ tồn tại trong file abbrev thành từ tương ứng
	 * Ví dụ vnđ chuyển thành việt nam đồng
	 * @param abbrev
	 *            the token to be expanded
	 * @param isCapital
	 *            whether the following token begins with a capital letter
	 * @return abbrev
	 */
	protected String expandAbbreviation(String abbrev, boolean isCapital) {
		String expAbb = abbrev.toLowerCase();
		if (!abbrevMap.containsKey(expAbb)) {
			logger.warn(String.format("Could not expand unknown abbreviation \"%s\", ignoring", abbrev));
			return abbrev;
		}
		expAbb = (String) this.abbrevMap.get(expAbb);
		String[] multiExp = expAbb.split(",");
		if (multiExp.length > 1) {
			if (isCapital) {
				expAbb = multiExp[0];
			} else {
				expAbb = multiExp[1];
			}
		}
		return expAbb;
	}
	/**
	 * Chuyển ngày thành chữ, ví dụ 20/10/2016 thành hai mươi tháng mười năm hai nghìn không trăm mười sáu. 
	 * @param date
	 * @return
	 * @throws ParseException
	 */
	protected String expandDate(String date) throws ParseException {
		String[] tempt = date.split("[\\/.]");
		ArrayList<String> rs = new ArrayList<String>();
		rs.add(NumberToCharacter.readRealNumber("" + Integer.parseInt(tempt[0])));
		rs.add("tháng");
		rs.add(NumberToCharacter.readRealNumber("" + Integer.parseInt(tempt[1])));
		rs.add("năm");
		rs.add(NumberToCharacter.readRealNumber(tempt[2]));
		return String.join(" ", rs);
	}

	/***
	 * Chuyển thời gian rút gọn thành chữ, ví dụ 11:50pm thành mười một giờ năm mươi phút chiều
	 * @param time
	 *            the token to be expanded
	 * @param isNextTokenTime
	 *            whether the following token contains am or pm
	 * @return theTime
	 */
	protected String expandTime(String time, boolean isNextTokenTime) {
		String theTime = "";
		String hour = "";
		Double pmHour;
		Matcher timeMatch = timePattern.matcher(time);
		timeMatch.find();
		// hour
		if (timeMatch.group(2) != null || timeMatch.group(3) != null) {
			hour = (timeMatch.group(2) != null) ? timeMatch.group(2) : timeMatch.group(3);
			if (hour.equals("00")) {
				hour = "12";
			}
			theTime += NumberToCharacter.readRealNumber(hour);
		} else {
			hour = (timeMatch.group(4) != null) ? timeMatch.group(4) : timeMatch.group(5);
			pmHour = Double.parseDouble(hour) - 12;
			if (pmHour == 0) {
				hour = "12";
				theTime += NumberToCharacter.readRealNumber(hour);
			} else {
				theTime += NumberToCharacter.readRealNumber(pmHour + "");
			}
		}
		// minutes
		if (timeMatch.group(7) != null && !isNextTokenTime) {
			if (!timeMatch.group(6).equals("00")) {

				theTime += " giờ " + NumberToCharacter.readRealNumber(timeMatch.group(6));

			}
			for (char c : timeMatch.group(7).replaceAll("\\.", "").toCharArray()) {
				theTime += " " + c;
			}
		} else if (!isNextTokenTime) {
			if (!timeMatch.group(6).equals("00")) {
				theTime += " giờ " + NumberToCharacter.readRealNumber(timeMatch.group(6));
			}
		} else {
			if (!timeMatch.group(6).equals("00")) {
				theTime += " giờ " + NumberToCharacter.readRealNumber(timeMatch.group(6));
			}
		}
		return theTime.replace(" p m", " phút chiều").replace(" a m", " phút sáng");
	}

	/**
	 * Chuyển token từ chữ và số thành toàn chữ (ví dụ ABC124 thành  A B C một hai bốn)
	 * @param wordnumseq
	 * @return
	 */
	protected String expandWordNumber(String wordnumseq) {
		String[] groups = wordnumseq.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
		ArrayList<String> rs = new ArrayList<String>();
		for (String g : groups) {
			if (g.matches("\\d+")) {
				String newTok = "";
				for (char c : g.toCharArray()) {
					newTok += NumberToCharacter.readRealNumber(String.valueOf(c)) + " ";
				}
				rs.add(newTok.trim());
			} else {
				for (int j = 0; j < g.length(); j++) {
					rs.add(g.charAt(j) + "");
				}
			}
		}
		return String.join(" ", rs);
	}
	/**
	 * Lấy tất cả những từ cần thay thế trong file abbrev.dat
	 * @return
	 * @throws IOException
	 */
	public static Map<Object, Object> loadAbbrevMap() throws IOException {
		Map<Object, Object> abbMap = new Properties();
		((Properties) abbMap)
				.load(new InputStreamReader(Preprocess.class.getResourceAsStream("preprocess/abbrev.dat"), "UTF-8"));
		return abbMap;
	}
}
