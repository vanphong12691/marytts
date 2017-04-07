package marytts.language.vi;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

import com.ibm.icu.util.ULocale;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * @author Tristan Hamilton
 *
 *         <p>
 *         Can process following formats:
 *         <ul>
 *         <li>cardinal (handled by real number)
 *         <li>ordinal
 *         <li>year (as a 4 digit number or any number followed by AD/BC
 *         variation)
 *         <li>currency
 *         <li>numberandword together
 *         <li>dashes (read each number singly) or (split into two words)
 *         <li>underscores
 *         <li>decimal point, minus symbol (real numbers) also handles &#037;,
 *         however Jtokeniser splits &#037; into separate tokens
 *         <li>time
 *         <li>dates (in format mm/dd/yyyy)
 *         <li>acronyms (only split into single characters, never expanded)
 *         <li>abbreviations (list of known expansions in resource
 *         preprocess/abbrev.dat, a properties file separated by whitespace. If
 *         an abbrev has two different expansions then the capitalized version
 *         comes first, followed by a comma)
 *         <li>contractions &rarr; first check lexicon, if not then &rarr; split
 *         and check if map contains contraction, if not then just remove
 *         apostrophe else &rarr; split before apostrophe into two tokens, use
 *         map to manually add ph &rarr; for 's if word ends in c,f,k,p,t then
 *         add ph &#061; s otherwise ph &#061; z
 *         <li>ampersand &amp;, "at" &#064; symbol, &rarr; symbols
 *         <li>urls &rarr; note that jtokeniser splits off http[s]?://
 *         <li>number ranges "18-35"
 *         <li>words without vowels &rarr; first check lexicon, if not then
 *         separate into single character tokens
 *         <li>#hashtags
 *         <li>single "A/a" character &rarr; if there is no next token or the
 *         next token is punctuation or next token string.length &#061;&#061; 1
 *         <li>should also as a last processing attempt, split by
 *         punctuation,symbols,etc. and attempt to process these tokens
 *         separately
 *         <li>durations hours:minutes:seconds(:milliseconds)
 *         <li>numbers followed by an s
 *         <li>punctuation &rarr; add ph attribute to tag to prevent
 *         phonemisation
 *         </ul>
 *         <p>
 *         May include:
 *         <ul>
 *         <li>roman numerals
 *         </ul>
 */
public class Preprocess extends InternalModule {

	// abbreviations map
	private Map<Object, Object> abbrevMap;

	// symbols map
	private static final Map<String, String> symbols;

	// icu4j stuff
	private RuleBasedNumberFormat rbnf;
	protected final String cardinalRule;
	protected final String ordinalRule;
	protected final String yearRule;
	
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
	private static final Pattern numberSPattern;

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
		numberSPattern = Pattern.compile("([0-9]+)([sS])");
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
		this.rbnf = new RuleBasedNumberFormat(ULocale.ENGLISH, RuleBasedNumberFormat.SPELLOUT);
		this.cardinalRule = "%spellout-numbering";
		this.ordinalRule = getOrdinalRuleName(rbnf);
		this.yearRule = getYearRuleName(rbnf);
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

			/*
			 * PRELIM FOR EACH NODE
			 */

			// to accommodate the first token being a url
			if (URLFirst) {
				t = (Element) tw.previousNode();
				URLFirst = false;
			}
			// save the original token text
			String origText = MaryDomUtils.tokenText(t);

			/*
			 * ACTUAL PROCESSING
			 */

			if (MaryDomUtils.tokenText(t).matches(datePattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandDate(MaryDomUtils.tokenText(t)));
				// number followed by s
			} else if (MaryDomUtils.tokenText(t).matches(numberWordPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandWordNumber(MaryDomUtils.tokenText(t)));
				// real number & currency
			} else if (MaryDomUtils.tokenText(t).matches(realNumPattern.pattern())
					&& !"-".equals(MaryDomUtils.tokenText(t))) {

				MaryDomUtils.setTokenText(t, NumberToCharacter.readRealNumber(MaryDomUtils.tokenText(t)));

			} else if (MaryDomUtils.tokenText(t).matches(acronymPattern.pattern())) {
				MaryDomUtils.setTokenText(t, expandAcronym(MaryDomUtils.tokenText(t)));
				// abbreviation
			} else if ((MaryDomUtils.tokenText(t).matches(abbrevPattern.pattern())
					|| this.abbrevMap.containsKey(MaryDomUtils.tokenText(t).toLowerCase())) && !isURL) {
				Element testAbbNode = MaryDomUtils.getNextSiblingElement((Element) t);
				boolean nextTokenIsCapital = false;
				if (testAbbNode != null && Character.isUpperCase(MaryDomUtils.tokenText(testAbbNode).charAt(0))) {
					nextTokenIsCapital = true;
				}
				MaryDomUtils.setTokenText(t, expandAbbreviation(MaryDomUtils.tokenText(t), nextTokenIsCapital));
				// time
			} else if (MaryDomUtils.tokenText(t).matches("(?i)" + timePattern.pattern())) {
				Element testTimeNode = MaryDomUtils.getNextSiblingElement((Element) t);
				boolean nextTokenIsTime = false;
				if (testTimeNode != null
						&& MaryDomUtils.tokenText(testTimeNode).matches("a\\.m\\.|AM|PM|am|pm|p\\.m\\.")) {
					nextTokenIsTime = true;
				}
				MaryDomUtils.setTokenText(t, expandTime(MaryDomUtils.tokenText(t), nextTokenIsTime));
				// duration
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
				// dot . for web and email addresses
			} else if (MaryDomUtils.tokenText(t).equals(".") && isURL) {
				MaryDomUtils.setTokenText(t, "chấm");
				webEmailTemp = webEmailTemp.replaceFirst("\\.", "chấm");
				if (!webEmailTemp.contains(".")) {
					isURL = false;
				}
				// symbols
			} else if (MaryDomUtils.tokenText(t).matches(symbolsPattern.pattern())) {
				MaryDomUtils.setTokenText(t, symbols.get(MaryDomUtils.tokenText(t)));
				// number ranges &rarr; before checking for dashes
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

	protected String expandNumber(double number) {
		this.rbnf.setDefaultRuleSet(cardinalRule);
		return this.rbnf.format(number);
	}

	protected String expandOrdinal(double number) {
		this.rbnf.setDefaultRuleSet(ordinalRule);
		return this.rbnf.format(number);
	}

	protected String expandDuration(String duration) {
		Matcher durMatcher = durationPattern.matcher(duration);
		durMatcher.find();
		String hrs = expandNumber(Double.parseDouble(durMatcher.group(1))) + " hours ";
		String mins = expandNumber(Double.parseDouble(durMatcher.group(2))) + " minutes ";
		String secs = expandNumber(Double.parseDouble(durMatcher.group(3))) + " seconds ";
		String ms = "";
		if (durMatcher.group(4) != null) {
			ms = "and " + expandNumber(Double.parseDouble(durMatcher.group(5))) + " milliseconds ";
		} else {
			secs = "and " + secs;
		}
		return hrs + mins + secs + ms;
	}

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
					temp += c;
				} else if (Character.isDigit(c) && temp.matches(".+[0-9]")) {
					temp += c;
				} else if (Character.isDigit(c)) {
					temp += " " + c;
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
	 * expands a digit followed by an s. e.g. 7s and 8s and the 60s
	 *
	 * @param numberS
	 *            numberS
	 * @return number
	 */
	protected String expandNumberS(String numberS) {
		Matcher numberSMatcher = numberSPattern.matcher(numberS);
		numberSMatcher.find();
		String number = expandNumber(Double.parseDouble(numberSMatcher.group(1)));
		if (number.endsWith("x")) {
			number += "es";
		} else if (number.endsWith("y")) {
			number = number.replace("y", "ies");
		} else {
			number += "s";
		}
		return number;
	}

	protected String splitContraction(String contraction) {
		int aposIndex = contraction.indexOf("'");
		String lemma = contraction.substring(0, aposIndex);
		String end = contraction.substring(aposIndex);
		return lemma + " " + end;
	}

	/***
	 *
	 * @param abbrev
	 *            the token to be expanded
	 * @param isCapital
	 *            whether the following token begins with a capital letter
	 * @return abbrev
	 */
	protected String expandAbbreviation(String abbrev, boolean isCapital) {
		String expAbb = abbrev.replaceAll("\\.", "").toLowerCase();
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
	 *
	 * @param time
	 *            the token to be expanded
	 * @param isNextTokenTime
	 *            whether the following token contains am or pm
	 * @return theTime
	 */
	protected String expandTime(String time, boolean isNextTokenTime) {
		boolean pastNoon = false;
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
			theTime += expandNumber(Double.parseDouble(hour));
		} else {
			pastNoon = true;
			hour = (timeMatch.group(4) != null) ? timeMatch.group(4) : timeMatch.group(5);
			pmHour = Double.parseDouble(hour) - 12;
			if (pmHour == 0) {
				hour = "12";
				theTime += expandNumber(Double.parseDouble(hour));
			} else {
				theTime += expandNumber(pmHour);
			}
		}
		// minutes
		if (timeMatch.group(7) != null && !isNextTokenTime) {
			if (!timeMatch.group(6).equals("00")) {
				if (timeMatch.group(6).matches("0\\d")) {
					theTime += " oh " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				} else {
					theTime += " " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
			}
			for (char c : timeMatch.group(7).replaceAll("\\.", "").toCharArray()) {
				theTime += " " + c;
			}
		} else if (!isNextTokenTime) {
			if (!timeMatch.group(6).equals("00")) {
				if (timeMatch.group(6).matches("0\\d")) {
					theTime += " oh " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				} else {
					theTime += " " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
			}
			theTime += !pastNoon ? " a m" : " p m";
		} else {
			if (!timeMatch.group(6).equals("00")) {
				if (timeMatch.group(6).matches("0\\d")) {
					theTime += " oh " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				} else {
					theTime += " " + expandNumber(Double.parseDouble(timeMatch.group(6)));
				}
			}
		}
		return theTime;
	}

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
	 * Try to extract the rule name for "expand ordinal" from the given
	 * RuleBasedNumberFormat.
	 * <p>
	 * The rule name is locale sensitive, but usually starts with
	 * "%spellout-ordinal".
	 *
	 * @param rbnf
	 *            The RuleBasedNumberFormat from where we will try to extract
	 *            the rule name.
	 * @return The rule name for "ordinal spell out".
	 */
	protected static String getOrdinalRuleName(final RuleBasedNumberFormat rbnf) {
		List<String> l = Arrays.asList(rbnf.getRuleSetNames());
		if (l.contains("%spellout-ordinal")) {
			return "%spellout-ordinal";
		} else if (l.contains("%spellout-ordinal-masculine")) {
			return "%spellout-ordinal-masculine";
		} else {
			for (String string : l) {
				if (string.startsWith("%spellout-ordinal")) {
					return string;
				}
			}
		}
		throw new UnsupportedOperationException(
				"The locale " + rbnf.getLocale(ULocale.ACTUAL_LOCALE) + " doesn't support ordinal spelling.");
	}

	/**
	 * Try to extract the rule name for "expand year" from the given
	 * RuleBasedNumberFormat.
	 * <p>
	 * The rule name is locale sensitive, but usually starts with
	 * "%spellout-numbering-year".
	 *
	 * @param rbnf
	 *            The RuleBasedNumberFormat from where we will try to extract
	 *            the rule name.
	 * @return The rule name for "year spell out".
	 */
	protected static String getYearRuleName(final RuleBasedNumberFormat rbnf) {
		List<String> l = Arrays.asList(rbnf.getRuleSetNames());
		if (l.contains("%spellout-numbering-year")) {
			return "%spellout-numbering-year";
		} else {
			for (String string : l) {
				if (string.startsWith("%spellout-numbering-year")) {
					return string;
				}
			}
		}
		throw new UnsupportedOperationException(
				"The locale " + rbnf.getLocale(ULocale.ACTUAL_LOCALE) + " doesn't support year spelling.");
	}

	public static Map<Object, Object> loadAbbrevMap() throws IOException {
		Map<Object, Object> abbMap = new Properties();
		((Properties) abbMap)
				.load(new InputStreamReader(Preprocess.class.getResourceAsStream("preprocess/abbrev.dat"), "UTF-8"));
		return abbMap;
	}
}
