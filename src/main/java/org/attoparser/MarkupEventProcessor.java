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
import java.util.Collections;
import java.util.List;

import org.attoparser.config.ParseConfiguration;


/*
 * Objects of this class are the first ones to receive events from the parser, and they are in charge of transmitting
 * these events to the markup handlers.
 *
 * This MarkupEventProcessor implements logic that allows the application of several features and restrictions in
 * XML and (especially) HTML markup. For this, it builds an element stack during parsing, which it uses to reference
 * events to their specific position in the original document.
 *
 * Note that, although MarkupParser's are stateless, objects of this class are STATEFUL just like markup handlers can
 * potentially be, and therefore a new MarkupEventProcessor object will be built for each parsing operation.
 *
 * @author Daniel Fernandez
 * @since 2.0.0
 */
final class MarkupEventProcessor implements ParsingAttributeSequenceUtil.IMarkupEventAttributeSequenceProcessor {


    private static final int DEFAULT_STACK_LEN = 10;
    private static final int DEFAULT_ATTRIBUTE_NAMES_LEN = 3;

    private final IMarkupHandler handler;
    private final ParseStatus status;

    private final boolean useStack;

    private final boolean autoClose;
    private final boolean requireBalancedElements;
    private final boolean requireNoUnmatchedCloseElements;

    private final ParseConfiguration.PrologParseConfiguration prologParseConfiguration;
    private final ParseConfiguration.UniqueRootElementPresence uniqueRootElementPresence;

    private final boolean caseSensitive;

    private final boolean requireWellFormedAttributeValues;
    private final boolean requireUniqueAttributesInElement;

    private final boolean validateProlog;
    private final boolean prologPresenceForbidden;
    private final boolean xmlDeclarationPresenceForbidden;
    private final boolean doctypePresenceForbidden;

    // Will be used as an element name cache in order to avoid creating a new
    // char[] object each time an element is pushed into the stack or an attribute
    // is processed to check its uniqueness.
    private final StructureNamesRepository structureNamesRepository;

    private char[][] elementStack;
    private int elementStackSize;

    private boolean validPrologXmlDeclarationRead = false;
    private boolean validPrologDocTypeRead = false;
    private boolean elementRead = false;
    private char[] rootElementName = null;
    private char[][] currentElementAttributeNames = null;
    private int currentElementAttributeNamesSize = 0;


    private boolean closeElementIsMatched = true;


    MarkupEventProcessor(final IMarkupHandler handler, final ParseStatus status, final ParseConfiguration parseConfiguration) {

        super();

        this.handler = handler;
        this.status = status;

        this.caseSensitive = parseConfiguration.isCaseSensitive();

        this.useStack = (!ParseConfiguration.ElementBalancing.NO_BALANCING.equals(parseConfiguration.getElementBalancing()) ||
                         parseConfiguration.getRequireUniqueAttributesInElement() ||
                         !ParseConfiguration.UniqueRootElementPresence.NOT_VALIDATED.equals(parseConfiguration.getUniqueRootElementPresence()));

        this.autoClose =
                (ParseConfiguration.ElementBalancing.AUTO_CLOSE.equals(parseConfiguration.getElementBalancing()) ||
                        ParseConfiguration.ElementBalancing.AUTO_CLOSE_REQUIRE_NO_UNMATCHED_CLOSE.equals(parseConfiguration.getElementBalancing()));
        this.requireBalancedElements =
                ParseConfiguration.ElementBalancing.REQUIRE_BALANCED.equals(parseConfiguration.getElementBalancing());
        this.requireNoUnmatchedCloseElements =
                (this.requireBalancedElements ||
                        ParseConfiguration.ElementBalancing.AUTO_CLOSE_REQUIRE_NO_UNMATCHED_CLOSE.equals(parseConfiguration.getElementBalancing()) ||
                        ParseConfiguration.ElementBalancing.REQUIRE_NO_UNMATCHED_CLOSE.equals(parseConfiguration.getElementBalancing()));

        this.prologParseConfiguration = parseConfiguration.getPrologParseConfiguration();

        this.prologParseConfiguration.validateConfiguration();

        this.uniqueRootElementPresence = parseConfiguration.getUniqueRootElementPresence();
        this.requireWellFormedAttributeValues = parseConfiguration.getRequireXmlWellFormedAttributeValues();
        this.requireUniqueAttributesInElement = parseConfiguration.getRequireUniqueAttributesInElement();

        this.validateProlog = this.prologParseConfiguration.isValidateProlog();
        this.prologPresenceForbidden = this.prologParseConfiguration.getPrologPresence().isForbidden();
        this.xmlDeclarationPresenceForbidden = this.prologParseConfiguration.getXmlDeclarationPresence().isRequired();
        this.doctypePresenceForbidden = this.prologParseConfiguration.getDoctypePresence().isRequired();

        if (this.useStack) {

            this.elementStack = new char[DEFAULT_STACK_LEN][];
            this.elementStackSize = 0;

            this.structureNamesRepository = new StructureNamesRepository();

        } else {

            this.elementStack = null;
            this.elementStackSize = 0;
            this.structureNamesRepository = null;

        }

    }




    void processDocumentStart(final long startTimeNanos, final int line, final int col)
            throws ParseException {
        this.handler.handleDocumentStart(startTimeNanos, line, col);
    }



    void processDocumentEnd(final long endTimeNanos, final long totalTimeNanos, final int line, final int col)
            throws ParseException {

        if (this.requireBalancedElements && this.elementStackSize > 0) {
            final char[] popped = popFromStack();
            throw new ParseException(
                "Malformed markup: element " +
                "\"" + new String(popped, 0, popped.length) + "\"" +
                " is never closed (no closing tag at the end of document)");
        }

        if (!this.elementRead && (
                (this.validPrologDocTypeRead && this.uniqueRootElementPresence.isDependsOnPrologDoctype()) ||
                this.uniqueRootElementPresence.isRequiredAlways())) {
            throw new ParseException(
                    "Malformed markup: no root element present");
        }

        if (this.useStack) {
            cleanStack(line, col);
        }

        this.handler.handleDocumentEnd(endTimeNanos, totalTimeNanos, line, col);

    }



    void processCDATASection(
            final char[] buffer,
            final int contentOffset, final int contentLen,
            final int outerOffset, final int outerLen,
            final int line, final int col)
            throws ParseException {
        this.handler.handleCDATASection(buffer, contentOffset, contentLen, outerOffset, outerLen, line, col);
    }




    void processComment(
            final char[] buffer,
            final int contentOffset, final int contentLen,
            final int outerOffset, final int outerLen,
            final int line, final int col)
            throws ParseException {
        this.handler.handleComment(buffer, contentOffset, contentLen, outerOffset, outerLen, line, col);
    }




    void processText(
            final char[] buffer,
            final int offset, final int len,
            final int line, final int col)
            throws ParseException {
        this.handler.handleText(buffer, offset, len, line, col);
    }




    void processProcessingInstruction(
            final char[] buffer,
            final int targetOffset, final int targetLen,
            final int targetLine, final int targetCol,
            final int contentOffset, final int contentLen,
            final int contentLine, final int contentCol,
            final int outerOffset, final int outerLen,
            final int line, final int col)
            throws ParseException {
        this.handler.handleProcessingInstruction(
                buffer,
                targetOffset, targetLen, targetLine, targetCol,
                contentOffset, contentLen, contentLine, contentCol,
                outerOffset, outerLen, line, col);
    }




    void processXmlDeclaration(
            final char[] buffer,
            final int keywordOffset, final int keywordLen,
            final int keywordLine, final int keywordCol,
            final int versionOffset, final int versionLen,
            final int versionLine, final int versionCol,
            final int encodingOffset, final int encodingLen,
            final int encodingLine, final int encodingCol,
            final int standaloneOffset, final int standaloneLen,
            final int standaloneLine, final int standaloneCol,
            final int outerOffset, final int outerLen,
            final int line, final int col)
            throws ParseException {

        if (this.validateProlog && (this.prologPresenceForbidden || this.xmlDeclarationPresenceForbidden)) {
            throw new ParseException(
                    "An XML Declaration has been found, but it wasn't allowed",
                    line, col);
        }

        if (this.validateProlog) {

            if (this.validPrologXmlDeclarationRead) {
                throw new ParseException(
                        "Malformed markup: Only one XML Declaration can appear in document",
                        line, col);
            }
            if (this.validPrologDocTypeRead) {
                throw new ParseException(
                        "Malformed markup: XML Declaration must appear before DOCTYPE",
                        line, col);
            }
            if (this.elementRead) {
                throw new ParseException(
                        "Malformed markup: XML Declaration must appear before any " +
                        "elements in document",
                        line, col);
            }

        }

        if (this.validateProlog) {
            this.validPrologXmlDeclarationRead = true;
        }

        this.handler.handleXmlDeclaration(buffer, keywordOffset, keywordLen, keywordLine,
                keywordCol, versionOffset, versionLen, versionLine, versionCol,
                encodingOffset, encodingLen, encodingLine, encodingCol,
                standaloneOffset, standaloneLen, standaloneLine, standaloneCol,
                outerOffset, outerLen, line, col);

    }


    void processStandaloneElementStart(
            final char[] buffer,
            final int nameOffset, final int nameLen,
            final boolean minimized,
            final int line, final int col)
            throws ParseException {

        if (this.useStack) {

            if (this.elementStackSize == 0) {
                checkValidRootElement(buffer, nameOffset, nameLen, line, col);
            }

            if (this.requireUniqueAttributesInElement) {
                this.currentElementAttributeNames = null;
                this.currentElementAttributeNamesSize = 0;
            }

            // This is a standalone element, no need to put into stack

        }

        /*
         * Perform the handling of the standalone element start
         * These events might require previous auto-* operations, in which case these
         * have to be performed and then the event launched again.
         */

        this.status.autoOpenCloseDone = false;
        this.status.autoCloseRequired = null;
        this.status.autoCloseLimits = null;
        this.status.avoidStacking = true; // Default for standalone elements is avoid stacking

        this.handler.handleStandaloneElementStart(buffer, nameOffset, nameLen, minimized, line, col);

        if (this.useStack) {
            if (this.status.autoCloseRequired != null) {
                // Auto-* operations
                unstack(this.status.autoCloseRequired, this.status.autoCloseLimits, line, col);
                // Re-launching of the event
                this.status.autoOpenCloseDone = true;
                this.handler.handleStandaloneElementStart(buffer, nameOffset, nameLen, minimized, line, col);
            }
            if (!this.status.avoidStacking) {
                pushToStack(buffer, nameOffset, nameLen);
            }
        } else {
            if (this.status.autoCloseRequired != null) {
                // We were required to perform auto* operations, but we have no stack, so we will
                // just launch the event again
                this.status.autoOpenCloseDone = true;
                this.handler.handleStandaloneElementStart(buffer, nameOffset, nameLen, minimized, line, col);
            }
        }

        this.status.autoOpenCloseDone = true;


    }

    void processStandaloneElementEnd(
            final char[] buffer,
            final int nameOffset, final int nameLen,
            final boolean minimized,
            final int line, final int col)
            throws ParseException {

        this.elementRead = true;
        this.handler.handleStandaloneElementEnd(buffer, nameOffset, nameLen, minimized, line, col);

    }


    void processOpenElementStart(
            final char[] buffer,
            final int nameOffset, final int nameLen,
            final int line, final int col)
            throws ParseException {

        if (this.useStack) {

            if (this.elementStackSize == 0) {
                checkValidRootElement(buffer, nameOffset, nameLen, line, col);
            }

            if (this.requireUniqueAttributesInElement) {
                this.currentElementAttributeNames = null;
                this.currentElementAttributeNamesSize = 0;
            }

        }

        /*
         * Perform the handling of the open element start
         * These events might require previous auto-* operations, in which case these
         * have to be performed and then the event launched again.
         */

        this.status.autoOpenCloseDone = false;
        this.status.autoCloseRequired = null;
        this.status.autoCloseLimits = null;
        this.status.avoidStacking = false; // Default for open elements is not to avoid stacking

        this.handler.handleOpenElementStart(buffer, nameOffset, nameLen, line, col);

        if (this.useStack) {
            if (this.status.autoCloseRequired != null) {
                // Auto-* operations
                unstack(this.status.autoCloseRequired, this.status.autoCloseLimits, line, col);
                // Re-launching of the event
                this.status.autoOpenCloseDone = true;
                this.handler.handleOpenElementStart(buffer, nameOffset, nameLen, line, col);
            }
            if (!this.status.avoidStacking) {
                // Can be an HTML void element
                pushToStack(buffer, nameOffset, nameLen);
            }
        } else {
            if (this.status.autoCloseRequired != null) {
                // We were required to perform auto* operations, but we have no stack, so we will
                // just launch the event again
                this.status.autoOpenCloseDone = true;
                this.handler.handleOpenElementStart(buffer, nameOffset, nameLen, line, col);
            }
        }

    }

    void processOpenElementEnd(
            final char[] buffer,
            final int nameOffset, final int nameLen,
            final int line, final int col)
            throws ParseException {

        this.elementRead = true;
        this.handler.handleOpenElementEnd(buffer, nameOffset, nameLen, line, col);

    }


    void processCloseElementStart(
            final char[] buffer,
            final int nameOffset, final int nameLen,
            final int line, final int col)
            throws ParseException {

        if (this.useStack) {

            this.closeElementIsMatched =
                    checkStackForElement(buffer, nameOffset, nameLen, line, col);

            if (this.requireUniqueAttributesInElement) {
                this.currentElementAttributeNames = null;
                this.currentElementAttributeNamesSize = 0;
            }

            if (this.closeElementIsMatched) {
                this.handler.handleCloseElementStart(buffer, nameOffset, nameLen, line, col);
                return;
            } else {
                this.handler.handleUnmatchedCloseElementStart(buffer, nameOffset, nameLen, line, col);
                return;
            }

        }

        this.handler.handleCloseElementStart(buffer, nameOffset, nameLen, line, col);

    }

    void processCloseElementEnd(
            final char[] buffer,
            final int nameOffset, final int nameLen,
            final int line, final int col)
            throws ParseException {

        this.elementRead = true;

        if (this.useStack && !this.closeElementIsMatched) {
            this.handler.handleUnmatchedCloseElementEnd(buffer, nameOffset, nameLen, line, col);
            return;
        }

        this.handler.handleCloseElementEnd(buffer, nameOffset, nameLen, line, col);

    }


    public void processAttribute(
            final char[] buffer,
            final int nameOffset, final int nameLen,
            final int nameLine, final int nameCol,
            final int operatorOffset, final int operatorLen,
            final int operatorLine, final int operatorCol,
            final int valueContentOffset, final int valueContentLen,
            final int valueOuterOffset, final int valueOuterLen,
            final int valueLine, final int valueCol)
            throws ParseException {

        if (this.useStack && this.requireUniqueAttributesInElement) {

            // Check attribute name is unique in this element
            if (this.currentElementAttributeNames == null) {
                // we only create this structure if there is at least one attribute
                this.currentElementAttributeNames = new char[DEFAULT_ATTRIBUTE_NAMES_LEN][];
            }
            for (int i = 0; i < this.currentElementAttributeNamesSize; i++) {

                if (TextUtil.equals(
                        this.caseSensitive,
                        this.currentElementAttributeNames[i], 0, this.currentElementAttributeNames[i].length,
                        buffer, nameOffset, nameLen)) {

                    throw new ParseException(
                            "Malformed markup: Attribute \"" + new String(buffer, nameOffset, nameLen) + "\" " +
                            "appears more than once in element",
                            nameLine, nameCol);

                }

            }
            if (this.currentElementAttributeNamesSize == this.currentElementAttributeNames.length) {
                // we need to grow the array!
                final char[][] newCurrentElementAttributeNames = new char[this.currentElementAttributeNames.length + DEFAULT_ATTRIBUTE_NAMES_LEN][];
                System.arraycopy(this.currentElementAttributeNames, 0, newCurrentElementAttributeNames, 0, this.currentElementAttributeNames.length);
                this.currentElementAttributeNames = newCurrentElementAttributeNames;
            }

            this.currentElementAttributeNames[this.currentElementAttributeNamesSize] =
                    this.structureNamesRepository.getStructureName(buffer, nameOffset, nameLen);

            this.currentElementAttributeNamesSize++;

        }


        if (this.requireWellFormedAttributeValues) {

            // Check there is an operator
            if (operatorLen == 0)  {
                throw new ParseException(
                        "Malformed markup: Attribute \"" + new String(buffer, nameOffset, nameLen) + "\" " +
                        "must include an equals (=) sign and a value surrounded by quotes",
                        operatorLine, operatorCol);
            }


            // Check attribute is surrounded by commas (double or single)
            if (valueOuterLen == 0 || valueOuterLen == valueContentLen)  {
                throw new ParseException(
                        "Malformed markup: Value for attribute \"" + new String(buffer, nameOffset, nameLen) + "\" " +
                        "must be surrounded by quotes",
                        valueLine, valueCol);
            }

        }

        this.handler.handleAttribute(
                buffer,
                nameOffset, nameLen, nameLine, nameCol,
                operatorOffset, operatorLen, operatorLine, operatorCol,
                valueContentOffset, valueContentLen, valueOuterOffset, valueOuterLen, valueLine, valueCol);

    }



    public void processInnerWhiteSpace(
            final char[] buffer,
            final int offset, final int len,
            final int line, final int col)
            throws ParseException {

        this.handler.handleInnerWhiteSpace(buffer, offset, len, line, col);

    }



    void processDocType(
            final char[] buffer,
            final int keywordOffset, final int keywordLen,
            final int keywordLine, final int keywordCol,
            final int elementNameOffset, final int elementNameLen,
            final int elementNameLine, final int elementNameCol,
            final int typeOffset, final int typeLen,
            final int typeLine, final int typeCol,
            final int publicIdOffset, final int publicIdLen,
            final int publicIdLine, final int publicIdCol,
            final int systemIdOffset, final int systemIdLen,
            final int systemIdLine, final int systemIdCol,
            final int internalSubsetOffset, final int internalSubsetLen,
            final int internalSubsetLine, final int internalSubsetCol,
            final int outerOffset, final int outerLen,
            final int outerLine, final int outerCol)
            throws ParseException {

        if (this.validateProlog) {

            if (this.prologPresenceForbidden || this.doctypePresenceForbidden) {
                throw new ParseException(
                        "A DOCTYPE clause has been found, but it wasn't allowed",
                        outerLine, outerCol);
            }

            if (this.validPrologDocTypeRead) {
                throw new ParseException(
                        "Malformed markup: Only one DOCTYPE clause can appear in document",
                        outerLine, outerCol);
            }

            if (this.elementRead) {
                throw new ParseException(
                        "Malformed markup: DOCTYPE must appear before any " +
                        "elements in document",
                        outerLine, outerCol);
            }

            if (this.prologParseConfiguration.isRequireDoctypeKeywordsUpperCase()) {

                if (keywordLen > 0) {
                    final int maxi = keywordOffset + keywordLen;
                    for (int i = keywordOffset; i < maxi; i++) {
                        if (Character.isLowerCase(buffer[i])) {
                            throw new ParseException(
                                    "Malformed markup: DOCTYPE requires upper-case " +
                                    "keywords (\"" + new String(buffer, keywordOffset, keywordLen) + "\" was found)",
                                    outerLine, outerCol);
                        }
                    }
                }

                if (typeLen > 0) {
                    final int maxi = typeOffset + typeLen;
                    for (int i = typeOffset; i < maxi; i++) {
                        if (Character.isLowerCase(buffer[i])) {
                            throw new ParseException(
                                    "Malformed markup: DOCTYPE requires upper-case " +
                                    "keywords (\"" + new String(buffer, typeOffset, typeLen) + "\" was found)",
                                    outerLine, outerCol);
                        }
                    }
                }

            }

        }

        if (this.useStack) {

            this.rootElementName =
                    this.structureNamesRepository.getStructureName(buffer, elementNameOffset, elementNameLen);

        }

        if (this.validateProlog) {
            this.validPrologDocTypeRead = true;
        }

        this.handler.handleDocType(
                    buffer,
                    keywordOffset, keywordLen, keywordLine, keywordCol,
                    elementNameOffset, elementNameLen, elementNameLine, elementNameCol,
                    typeOffset, typeLen, typeLine, typeCol,
                    publicIdOffset, publicIdLen, publicIdLine, publicIdCol,
                    systemIdOffset, systemIdLen, systemIdLine, systemIdCol,
                    internalSubsetOffset, internalSubsetLen, internalSubsetLine, internalSubsetCol,
                    outerOffset, outerLen, outerLine, outerCol);

    }





    private void checkValidRootElement(
            final char[] buffer, final int offset, final int len, final int line, final int col)
            throws ParseException {

        if (!this.validateProlog) {

            if (this.elementRead && this.uniqueRootElementPresence.isRequiredAlways()) {
                // We are not validating the prolog, but anyway we required only one element root
                // and it seems there are several.
                throw new ParseException(
                        "Malformed markup: Only one root element is allowed",
                        line, col);
            }

            // Nothing else to check.
            return;

        }

        // We don't need to check the possibility of having parsed forbidden XML Decl or DOCTYPE
        // because this has already been checked when the corresponding events were triggered.

        if (this.validPrologDocTypeRead) {

            if (this.elementRead) {
                // If we have a DOCTYPE, we will have a root element name and therefore we will
                // only allow one root element. But it seems there are several.
                throw new ParseException(
                        "Malformed markup: Only one root element (with name \"" + new String(this.rootElementName) + "\" is allowed",
                        line, col);
            }

            if (!TextUtil.equals(this.caseSensitive, this.rootElementName, 0, this.rootElementName.length, buffer, offset, len)) {
                throw new ParseException(
                    "Malformed markup: Root element should be \"" + new String(this.rootElementName) + "\", " +
                    "but \"" + new String(buffer, offset, len) + "\" has been found",
                    line, col);
            }

        }

    }



    private boolean checkStackForElement(
            final char[] buffer, final int offset, final int len, final int line, final int col)
            throws ParseException {

        int peekDelta = 0;
        char[] peek = peekFromStack(peekDelta);

        while (peek != null) {

            if (TextUtil.equals(this.caseSensitive, peek, 0, peek.length, buffer, offset, len)) {

                // We found the corresponding opening element, so we execute all pending auto-close events
                // (if needed) and return true (meaning the close element has a matching open element).

                for (int i = 0; i < peekDelta; i++) {
                    peek = popFromStack();
                    if (this.autoClose) {
                        this.handler.handleAutoCloseElementStart(peek, 0, peek.length, line, col);
                        this.handler.handleAutoCloseElementEnd(peek, 0, peek.length, line, col);
                    }
                }
                popFromStack();

                return true;

            }

            // does not match...

            if (this.requireBalancedElements) {
                throw new ParseException(
                        "Malformed markup: element " +
                        "\"" + new String(peek, 0, peek.length) + "\"" +
                        " is never closed", line, col);
            }

            peek = peekFromStack(++peekDelta);

        }

        // closing element at the root level
        if (this.requireNoUnmatchedCloseElements) {
            throw new ParseException(
                    "Malformed markup: closing element " +
                    "\"" + new String(buffer, offset, len) + "\"" +
                    " is never open", line, col);
        }

        // Return false because the close element has no matching open element
        return false;

    }




    private void cleanStack(final int line, final int col)
            throws ParseException {

        if (this.elementStackSize > 0) {

            // When we arrive here we know that "requireBalancedElements" is
            // false. If it were true, an exception would have been raised before.

            char[] popped = popFromStack();

            while (popped != null) {

                if (this.autoClose) {
                    this.handler.handleAutoCloseElementStart(popped, 0, popped.length, line, col);
                    this.handler.handleAutoCloseElementEnd(popped, 0, popped.length, line, col);
                }

                popped = popFromStack();

            }

        }

    }



    private void unstack(
            final char[][] unstackElements, final char[][] unstackLimits, final int line, final int col)
            throws ParseException {

        int peekDelta = 0;
        int unstackCount = 0;
        char[] peek = peekFromStack(peekDelta);

        int i;

        while (peek != null) {

            if (unstackLimits != null) {
                // First check whether we found a limit
                for (i = 0; i < unstackLimits.length; i++) {
                    if (TextUtil.equals(this.caseSensitive, unstackLimits[i], peek)) {
                        // Just found a limit, we should stop computing unstacking here
                        peek = null; // This will make us exit the loop
                        break;
                    }
                }
            }

            if (peek != null) {

                // Check whether this is an element we must close
                for (i = 0; i < unstackElements.length; i++) {
                    if (TextUtil.equals(this.caseSensitive, unstackElements[i], peek)) {
                        // This is an element we must unstack, so we should mark unstackCount
                        unstackCount = peekDelta + 1;
                        break;
                    }
                }

                // Feed the loop
                peek = peekFromStack(++peekDelta);

            }

        }


        for (i = 0; i < unstackCount; i++) {

            peek = popFromStack();

            if (this.requireBalancedElements) {
                throw new ParseException(
                        "Malformed markup: element " +
                                "\"" + new String(peek, 0, peek.length) + "\"" +
                                " is not closed where it should be", line, col);
            }

            if (this.autoClose) {
                this.handler.handleAutoCloseElementStart(peek, 0, peek.length, line, col);
                this.handler.handleAutoCloseElementEnd(peek, 0, peek.length, line, col);
            }

        }

    }



    private void pushToStack(
            final char[] buffer, final int offset, final int len) {

        if (this.elementStackSize == this.elementStack.length) {
            growStack();
        }

        this.elementStack[this.elementStackSize] =
                this.structureNamesRepository.getStructureName(buffer, offset, len);

        this.elementStackSize++;

    }


    private char[] peekFromStack(final int delta) {
        if (this.elementStackSize <= delta) {
            return null;
        }
        return this.elementStack[(this.elementStackSize - 1) - delta];
    }


    private char[] popFromStack() {
        if (this.elementStackSize == 0) {
            return null;
        }
        final char[] popped = this.elementStack[this.elementStackSize - 1];
        this.elementStack[this.elementStackSize - 1] = null;
        this.elementStackSize--;
        return popped;
    }


    private void growStack() {

        final int newStackLen = this.elementStack.length + DEFAULT_STACK_LEN;
        final char[][] newStack = new char[newStackLen][];
        System.arraycopy(this.elementStack, 0, newStack, 0, this.elementStack.length);
        this.elementStack = newStack;

    }







    /*
     * In-instance repository for structure names (element + attribute names).
     *
     * This class is NOT thread-safe. Should only be used inside a specific handler
     * instance/thread and only during a single execution.
     */
    static final class StructureNamesRepository {

        private final List<char[]> repository;


        StructureNamesRepository() {
            super();
            this.repository = new ArrayList<char[]>(50);
        }


        char[] getStructureName(final char[] text, final int offset, final int len) {

            final int index = TextUtil.binarySearchCharArray(true, this.repository, text, offset, len);

            if (index >= 0) {
                return this.repository.get(index);
            }

            /*
             * NOT FOUND. We need to store the text
             */
            return storeStructureName(index, text, offset, len);

        }


        private char[] storeStructureName(final int index, final char[] text, final int offset, final int len) {

            // We rely on the static structure name cache, just in case it is a standard HTML structure name.
            // Note the StandardNamesRepository will create the new char[] if not found, so no need to null-check.
            final char[] structureName = StandardNamesRepository.getStructureName(text, offset, len);

            // binary Search returned (-(insertion point) - 1)
            this.repository.add(((index + 1) * -1), structureName);

            return structureName;

        }

    }




    /*
     *     This class is IMMUTABLE, and therefore thread-safe. Will be used in a static manner by all
     *     threads which require the use of a repository of standard names (HTML names, in this case).
     */
    static final class StandardNamesRepository {


        private static final char[][] REPOSITORY;


        static {

            final List<String> names = new ArrayList<String>(150);
            // Add all the standard HTML element (tag) names
            names.addAll(HtmlNames.ALL_STANDARD_ELEMENT_NAMES);
            // We know all standard element names are lowercase, so let's cache them uppercase too
            for (final String name : HtmlNames.ALL_STANDARD_ELEMENT_NAMES) {
                names.add(name.toUpperCase());
            }
            // Add all the standard HTML attribute names
            names.addAll(HtmlNames.ALL_STANDARD_ATTRIBUTE_NAMES);
            // We know all standard attribute names are lowercase, so let's cache them uppercase too
            for (final String name : HtmlNames.ALL_STANDARD_ATTRIBUTE_NAMES) {
                names.add(name.toUpperCase());
            }
            Collections.sort(names);

            REPOSITORY = new char[names.size()][];

            for (int i = 0; i < names.size(); i++) {
                final String name = names.get(i);
                REPOSITORY[i] = name.toCharArray();
            }

        }


        static char[] getStructureName(final char[] text, final int offset, final int len) {

            final int index = TextUtil.binarySearchCharArray(true, REPOSITORY, text, offset, len);

            if (index < 0) {
                final char[] structureName = new char[len];
                System.arraycopy(text, offset, structureName, 0, len);
                return structureName;
            }

            return REPOSITORY[index];

        }


        private StandardNamesRepository() {
            super();
        }

    }



}