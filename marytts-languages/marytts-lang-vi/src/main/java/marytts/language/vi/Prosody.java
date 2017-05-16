/**
 * Copyright 2000-2006 DFKI GmbH.
 * All Rights Reserved.  Use is subject to license terms.
 *
 * This file is part of MARY TTS.
 *
 * MARY TTS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package marytts.language.vi;

import java.util.Locale;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.TreeWalker;

import marytts.datatypes.MaryDataType;
import marytts.modules.ProsodyGeneric;
import marytts.server.MaryProperties;
import marytts.util.dom.DomUtils;
import marytts.util.dom.MaryDomUtils;
import marytts.util.dom.NameNodeFilter;
import marytts.vi.util.TextToPhone;


public class Prosody extends ProsodyGeneric {
	
	public Prosody() {
		super(MaryDataType.PHONEMES, MaryDataType.INTONATION, new Locale("vi"), MaryProperties.localePrefix(new Locale("vi"))
				+ ".prosody.tobipredparams", MaryProperties.localePrefix(new Locale("vi")) + ".prosody.accentPriorities",
				MaryProperties.localePrefix(new Locale("vi")) + ".prosody.syllableaccents", MaryProperties
						.localePrefix(new Locale("vi")) + ".prosody.paragraphdeclination");
	}
	@Override
	protected synchronized void getAccentPosition(Element token, NodeList tokens, int position, String sentenceType,
			String specialPositionType) {
		String tokenText = MaryDomUtils.tokenText(token); // text of current token

		Element ruleList = null;
		// only the "accentposition" rules are relevant
		ruleList = (Element) tobiPredMap.get("accentposition");
		// search for concrete rules, with tag "rule"
		TreeWalker tw = ((DocumentTraversal) ruleList.getOwnerDocument()).createTreeWalker(ruleList, NodeFilter.SHOW_ELEMENT,
				new NameNodeFilter(new String[] { "rule" }), false);

		boolean rule_fired = false;
		String accent = ""; // default
		Element rule = null;

		// search for appropriate rules; the top rule has highest prority
		// if a rule fires (that is: all the conditions are fulfilled),
		// the accent value("tone","force" or "") is assigned and the loop stops
		// if no rule is found, the accent value is ""

		while (!rule_fired && (rule = (Element) tw.nextNode()) != null) {
			// rule = the whole rule
			// currentRulePart = part of the rule (type of condition (f.e. attributes pos="NN") or action)
			Element currentRulePart = DomUtils.getFirstChildElement(rule);

			while (!rule_fired && currentRulePart != null) {

				boolean conditionSatisfied = false;

				// if rule part with tag "action": accent assignment
				if (currentRulePart.getTagName().equals("action")) {
					accent = currentRulePart.getAttribute("accent");
					
					token.setAttribute("accent", TextToPhone.getTone(tokenText));
					rule_fired = true;
					break;
				}
				// check if the condition is satisfied
				conditionSatisfied = checkRulePart(currentRulePart, token, tokens, position, sentenceType, specialPositionType,
						tokenText);
				if (!conditionSatisfied)
					break; // condition violated, try next rule

				// the previous conditions are satisfied --> check the next rule part
				currentRulePart = DomUtils.getNextSiblingElement(currentRulePart);
			} // while loop that checks the rule parts
		} // while loop that checks the whole rule
	}
}
