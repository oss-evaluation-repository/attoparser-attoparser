/*
 * =============================================================================
 * 
 *   Copyright (c) 2012-2014, The ATTOPARSER team (http://www.attoparser.org)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * 
 * =============================================================================
 */
package org.attoparser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/*
 * Repository class for all the standard HTML elements, modeled as objects implementing
 * the HtmlElement by means of one of its concrete implementations.
 *
 * By using one specific implementation for each element, that HTML element is given
 * some behaviour. E.g., <script> tags should consider their body as CDATA and
 * not PCDATA (i.e. their body should not be parsed), and that's why the SCRIPT
 * constant object is an implementation of HtmlCDATAContentElement.
 * 
 * @author Daniel Fernandez
 * @since 2.0.0
 */
final class HtmlElements {

    private static final HtmlElementRepository ELEMENTS = new HtmlElementRepository();


    // Set containing all the standard elements, for posible external reference
    static final Set<HtmlElement> ALL_STANDARD_ELEMENTS;


    // Root
    static final HtmlElement HTML = new HtmlElement("html");
    
    // Document metadata
    static final HtmlElement HEAD = new HtmlElement("head");
    static final HtmlElement TITLE = new HtmlElement("title");
    static final HtmlElement BASE = new HtmlVoidElement("base");
    static final HtmlElement LINK = new HtmlVoidElement("link");
    static final HtmlElement META = new HtmlVoidElement("meta");
    static final HtmlElement STYLE = new HtmlCDATAContentElement("style");
    
    // Scripting
    static final HtmlElement SCRIPT = new HtmlCDATAContentElement("script");
    static final HtmlElement NOSCRIPT = new HtmlElement("noscript");
    
    // Sections
    static final HtmlElement BODY = new HtmlElement("body");
    static final HtmlElement ARTICLE = new HtmlAutoCloserElement("article", new String[] { "p" }, null);
    static final HtmlElement SECTION = new HtmlAutoCloserElement("section", new String[] { "p" }, null);
    static final HtmlElement NAV = new HtmlAutoCloserElement("nav", new String[] { "p" }, null);
    static final HtmlElement ASIDE = new HtmlAutoCloserElement("aside", new String[] { "p" }, null);
    static final HtmlElement H1 = new HtmlAutoCloserElement("h1", new String[] { "p" }, null);
    static final HtmlElement H2 = new HtmlAutoCloserElement("h2", new String[] { "p" }, null);
    static final HtmlElement H3 = new HtmlAutoCloserElement("h3", new String[] { "p" }, null);
    static final HtmlElement H4 = new HtmlAutoCloserElement("h4", new String[] { "p" }, null);
    static final HtmlElement H5 = new HtmlAutoCloserElement("h5", new String[] { "p" }, null);
    static final HtmlElement H6 = new HtmlAutoCloserElement("h6", new String[] { "p" }, null);
    static final HtmlElement HGROUP = new HtmlAutoCloserElement("hgroup", new String[] { "p" }, null);
    static final HtmlElement HEADER = new HtmlAutoCloserElement("header", new String[] { "p" }, null);
    static final HtmlElement FOOTER = new HtmlAutoCloserElement("footer", new String[] { "p" }, null);
    static final HtmlElement ADDRESS = new HtmlAutoCloserElement("address", new String[] { "p" }, null);
    static final HtmlElement MAIN = new HtmlAutoCloserElement("main", new String[] { "p" }, null);

    // Grouping content
    static final HtmlElement P = new HtmlAutoCloserElement("p", new String[] { "p" }, null);
    static final HtmlElement HR = new HtmlVoidAutoCloserElement("hr", new String[] { "p" }, null);
    static final HtmlElement PRE = new HtmlAutoCloserElement("pre", new String[] { "p" }, null);
    static final HtmlElement BLOCKQUOTE = new HtmlAutoCloserElement("blockquote", new String[] { "p" }, null);
    static final HtmlElement OL = new HtmlAutoCloserElement("ol", new String[] { "p" }, null);
    static final HtmlElement UL = new HtmlAutoCloserElement("ul", new String[] { "p" }, null);
    static final HtmlElement LI = new HtmlAutoCloserElement("li", new String[] { "li" }, new String[] { "ul", "ol" });
    static final HtmlElement DL = new HtmlAutoCloserElement("dl", new String[] { "p" }, null);
    static final HtmlElement DT = new HtmlAutoCloserElement("dt", new String[] { "dt", "dd" }, new String[] { "dl" });
    static final HtmlElement DD = new HtmlAutoCloserElement("dd", new String[] { "dt", "dd" }, new String[] { "dl" });
    static final HtmlElement FIGURE = new HtmlElement("figure");
    static final HtmlElement FIGCAPTION = new HtmlElement("figcaption");
    static final HtmlElement DIV = new HtmlAutoCloserElement("div", new String[] { "p" }, null);
    
    // Text-level semantics
    static final HtmlElement A = new HtmlElement("a");
    static final HtmlElement EM = new HtmlElement("em");
    static final HtmlElement STRONG = new HtmlElement("strong");
    static final HtmlElement SMALL = new HtmlElement("small");
    static final HtmlElement S = new HtmlElement("s");
    static final HtmlElement CITE = new HtmlElement("cite");
    static final HtmlElement G = new HtmlElement("g");
    static final HtmlElement DFN = new HtmlElement("dfn");
    static final HtmlElement ABBR = new HtmlElement("abbr");
    static final HtmlElement TIME = new HtmlElement("time");
    static final HtmlElement CODE = new HtmlElement("code");
    static final HtmlElement VAR = new HtmlElement("var");
    static final HtmlElement SAMP = new HtmlElement("samp");
    static final HtmlElement KBD = new HtmlElement("kbd");
    static final HtmlElement SUB = new HtmlElement("sub");
    static final HtmlElement SUP = new HtmlElement("sup");
    static final HtmlElement I = new HtmlElement("i");
    static final HtmlElement B = new HtmlElement("b");
    static final HtmlElement U = new HtmlElement("u");
    static final HtmlElement MARK = new HtmlElement("mark");
    static final HtmlElement RUBY = new HtmlElement("ruby");
    static final HtmlElement RB = new HtmlAutoCloserElement("rb", new String[] { "rb", "rt", "rtc", "rp" }, new String[] { "ruby" });
    static final HtmlElement RT = new HtmlAutoCloserElement("rt", new String[] { "rb", "rt", "rp" }, new String[] { "ruby", "rtc" });
    static final HtmlElement RTC = new HtmlAutoCloserElement("rtc", new String[] { "rb", "rt", "rtc", "rp" }, new String[] { "ruby" });
    static final HtmlElement RP = new HtmlAutoCloserElement("rp", new String[] { "rb", "rt", "rp" }, new String[] { "ruby", "rtc" });
    static final HtmlElement BDI = new HtmlElement("bdi");
    static final HtmlElement BDO = new HtmlElement("bdo");
    static final HtmlElement SPAN = new HtmlElement("span");
    static final HtmlElement BR = new HtmlVoidElement("br");
    static final HtmlElement WBR = new HtmlVoidElement("wbr");

    // Edits
    static final HtmlElement INS = new HtmlElement("ins");
    static final HtmlElement DEL = new HtmlElement("del");
    
    // Embedded content
    static final HtmlElement IMG = new HtmlVoidElement("img");
    static final HtmlElement IFRAME = new HtmlElement("iframe");
    static final HtmlElement EMBED = new HtmlVoidElement("embed");
    static final HtmlElement OBJECT = new HtmlElement("object");
    static final HtmlElement PARAM = new HtmlVoidElement("param");
    static final HtmlElement VIDEO = new HtmlElement("video");
    static final HtmlElement AUDIO = new HtmlElement("audio");
    static final HtmlElement SOURCE = new HtmlVoidElement("source");
    static final HtmlElement TRACK = new HtmlVoidElement("track");
    static final HtmlElement CANVAS = new HtmlElement("canvas");
    static final HtmlElement MAP = new HtmlElement("map");
    static final HtmlElement AREA = new HtmlVoidElement("area");
    
    // Tabular data
    static final HtmlElement TABLE = new HtmlAutoCloserElement("table", new String[] { "p" }, null);
    static final HtmlElement CAPTION = new HtmlAutoCloserElement("caption", new String[] { "tr", "td", "thead", "tfoot", "tbody", "caption", "colgroup" }, new String[] { "table" });
    static final HtmlElement COLGROUP = new HtmlAutoCloserElement("colgroup", new String[] { "tr", "td", "thead", "tfoot", "tbody", "caption", "colgroup" }, new String[] { "table" });
    static final HtmlElement COL = new HtmlVoidElement("col");
    static final HtmlElement TBODY = new HtmlAutoCloserElement("tbody", new String[] { "tr", "td", "thead", "tfoot", "tbody", "caption", "colgroup" }, new String[] { "table" });
    static final HtmlElement THEAD = new HtmlAutoCloserElement("thead", new String[] { "tr", "td", "thead", "tfoot", "tbody", "caption", "colgroup" }, new String[] { "table" });
    static final HtmlElement TFOOT = new HtmlAutoCloserElement("tfoot", new String[] { "tr", "td", "thead", "tfoot", "tbody", "caption", "colgroup" }, new String[] { "table" });
    static final HtmlElement TR = new HtmlAutoCloserElement("tr", new String[] { "tr", "caption", "colgroup" }, new String[] { "table", "thead", "tbody", "tfoot" });
    static final HtmlElement TD = new HtmlAutoCloserElement("td", new String[] { "td", "th" }, new String[] { "tr" });
    static final HtmlElement TH = new HtmlAutoCloserElement("th", new String[] { "td", "th" }, new String[] { "tr" });
    
    // Forms
    static final HtmlElement FORM = new HtmlAutoCloserElement("form", new String[] { "p" }, null);
    static final HtmlElement FIELDSET = new HtmlAutoCloserElement("fieldset", new String[] { "p" }, null);
    static final HtmlElement LEGEND = new HtmlElement("legend");
    static final HtmlElement LABEL = new HtmlElement("label");
    static final HtmlElement INPUT = new HtmlVoidElement("input");
    static final HtmlElement BUTTON = new HtmlElement("button");
    static final HtmlElement SELECT = new HtmlElement("select");
    static final HtmlElement DATALIST = new HtmlElement("datalist");
    static final HtmlElement OPTGROUP = new HtmlAutoCloserElement("optgroup", new String[] { "optgroup", "option" }, new String[] { "select" });
    static final HtmlElement OPTION = new HtmlAutoCloserElement("option", new String[] { "option" }, new String[] { "select", "optgroup", "datalist" });
    static final HtmlElement TEXTAREA = new HtmlElement("textarea");
    static final HtmlElement KEYGEN = new HtmlVoidElement("keygen");
    static final HtmlElement OUTPUT = new HtmlElement("output");
    static final HtmlElement PROGRESS = new HtmlElement("progress");
    static final HtmlElement METER = new HtmlElement("meter");
    
    // Interactive elements
    static final HtmlElement DETAILS = new HtmlElement("details");
    static final HtmlElement SUMMARY = new HtmlElement("summary");
    static final HtmlElement COMMAND = new HtmlElement("command");
    static final HtmlElement MENU = new HtmlAutoCloserElement("menu", new String[] { "p" }, null);
    static final HtmlElement MENUITEM = new HtmlVoidElement("menuitem");
    static final HtmlElement DIALOG = new HtmlElement("dialog");
    
    

    static {

        ALL_STANDARD_ELEMENTS =
                Collections.unmodifiableSet(new LinkedHashSet<HtmlElement>(Arrays.asList(
                        new HtmlElement[] {
                                HTML, HEAD, TITLE, BASE, LINK, META, STYLE, SCRIPT, NOSCRIPT, BODY, ARTICLE,
                                SECTION, NAV, ASIDE, H1, H2, H3, H4, H5, H6, HGROUP, HEADER, FOOTER,
                                ADDRESS, P, HR, PRE, BLOCKQUOTE, OL, UL, LI, DL, DT, DD, FIGURE,
                                FIGCAPTION, DIV, A, EM, STRONG, SMALL, S, CITE, G, DFN, ABBR, TIME,
                                CODE, VAR, SAMP, KBD, SUB, SUP, I, B, U, MARK, RUBY, RB, RT, RTC,
                                RP, BDI, BDO, SPAN, BR, WBR, INS, DEL, IMG, IFRAME, EMBED, OBJECT,
                                PARAM, VIDEO, AUDIO, SOURCE, TRACK, CANVAS, MAP, AREA, TABLE, CAPTION,
                                COLGROUP, COL, TBODY, THEAD, TFOOT, TR, TD, TH, FORM, FIELDSET, LEGEND, LABEL,
                                INPUT, BUTTON, SELECT, DATALIST, OPTGROUP, OPTION, TEXTAREA, KEYGEN, OUTPUT, PROGRESS,
                                METER, DETAILS, SUMMARY, COMMAND, MENU, MENUITEM, DIALOG, MAIN
                        })));

        /*
         * Register the standard elements at the element repository, in order to initialize it
         */
        for (final HtmlElement element : ALL_STANDARD_ELEMENTS) {
            ELEMENTS.storeElement(element);
        }


    }


    /*
     * Note this will always be case-insensitive, because we are dealing with HTML.
     */
    static HtmlElement forName(final char[] elementNameBuffer, final int offset, final int len) {
        if (elementNameBuffer == null) {
            throw new IllegalArgumentException("Buffer cannot be null");
        }
        return ELEMENTS.getElement(elementNameBuffer, offset, len);
    }


    

    
    
    private HtmlElements() {
        super();
    }
    
    

    /*
     * This repository class is thread-safe. The reason for this is that it not only contains the
     * standard elements, but will also contain new instances of HtmlElement created during parsing (created
     * when asking the repository for them when they do not exist yet. As any thread can create a new element,
     * this has to be lock-protected.
     */
    static final class HtmlElementRepository {

        private final List<HtmlElement> repository;

        private final ReadWriteLock lock = new ReentrantReadWriteLock(true);
        private final Lock readLock = this.lock.readLock();
        private final Lock writeLock = this.lock.writeLock();


        HtmlElementRepository() {
            this.repository = new ArrayList<HtmlElement>(150);
        }



        HtmlElement getElement(final char[] text, final int offset, final int len) {

            this.readLock.lock();
            try {

                final int index = binarySearch(this.repository, text, offset, len);

                if (index >= 0) {
                    return this.repository.get(index);
                }

            } finally {
                this.readLock.unlock();
            }


            /*
             * NOT FOUND. We need to obtain a write lock and store the text
             */
            this.writeLock.lock();
            try {
                return storeElement(text, offset, len);
            } finally {
                this.writeLock.unlock();
            }

        }


        private HtmlElement storeElement(final char[] text, final int offset, final int len) {

            final int index = binarySearch(this.repository, text, offset, len);
            if (index >= 0) {
                // It was already added while we were waiting for the lock!
                return this.repository.get(index);
            }

            final HtmlElement element = new HtmlElement(new String(text, offset, len).toLowerCase());

            // binary Search returned (-(insertion point) - 1)
            this.repository.add(((index + 1) * -1), element);

            return element;

        }


        private HtmlElement storeElement(final HtmlElement element) {

            // This method will only be called from within the HtmlElements class itself, during initialization of
            // standard elements.

            this.repository.add(element);
            Collections.sort(this.repository,ElementComparator.INSTANCE);

            return element;

        }



        private static int binarySearch(final List<HtmlElement> values,
                                        final char[] text, final int offset, final int len) {

            int low = 0;
            int high = values.size() - 1;

            int mid, cmp;
            char[] midVal;

            while (low <= high) {

                mid = (low + high) >>> 1;
                midVal = values.get(mid).name;

                cmp = TextUtil.compareTo(false, midVal, 0, midVal.length, text, offset, len);

                if (cmp < 0) {
                    low = mid + 1;
                } else if (cmp > 0) {
                    high = mid - 1;
                } else {
                    // Found!!
                    return mid;
                }

            }

            return -(low + 1);  // Not Found!! We return (-(insertion point) - 1), to guarantee all non-founds are < 0

        }


        private static class ElementComparator implements Comparator<HtmlElement> {

            private static ElementComparator INSTANCE = new ElementComparator();

            public int compare(final HtmlElement o1, final HtmlElement o2) {
                return TextUtil.compareTo(false, o1.name, o2.name);
            }
        }

    }




}