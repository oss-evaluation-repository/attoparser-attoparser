/*
 * =============================================================================
 * 
 *   Copyright (c) 2012, The ATTOPARSER team (http://www.attoparser.org)
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
package org.attoparser.markup;

import org.attoparser.AbstractBufferedAttoParser;
import org.attoparser.AttoParseException;
import org.attoparser.IAttoHandler;



/**
 * 
 * @author Daniel Fern&aacute;ndez
 * 
 * @since 1.0
 *
 */
public final class MarkupAttoParser extends AbstractBufferedAttoParser {


    
    public MarkupAttoParser() {
        super();
    }
    

    
    
    
    
    @Override
    protected final BufferParseResult parseBuffer(
            final char[] buffer, final int offset, final int len, 
            final IAttoHandler handler, final int line, final int col) 
            throws AttoParseException {


        MarkupParsingLocator locator = new MarkupParsingLocator(line, col);
        
        int currentLine = locator.getLine();
        int currentCol = locator.getCol();
        
        final int maxi = offset + len;
        int i = offset;
        int current = i;
        
        boolean inStructure = false;
        
        boolean inOpenElement = false;
        boolean inCloseElement = false;
        boolean inComment = false;
        boolean inCdata = false;
        
        int tagStart = -1;
        int tagEnd = -1;
        
        while (i < maxi) {
            
            currentLine = locator.getLine();
            currentCol = locator.getCol();
            
            inStructure =
                    (inOpenElement || inCloseElement || inComment || inCdata);
            
            if (!inStructure) {
                
                tagStart = MarkupParsingUtil.findNext(buffer, i, maxi, '<', false, locator);
                
                if (tagStart == -1) {
                    return new BufferParseResult(current, currentLine, currentCol, false);
                }

                inOpenElement = MarkupParsingUtil.isOpenElementStart(buffer, tagStart, maxi);
                if (!inOpenElement) {
                    inCloseElement = MarkupParsingUtil.isCloseElementStart(buffer, tagStart, maxi);
                    if (!inCloseElement) {
                        inComment = MarkupParsingUtil.isCommentStart(buffer, tagStart, maxi);
                        if (!inComment) {
                            inCdata = MarkupParsingUtil.isCdataStart(buffer, tagStart, maxi);
                        }
                    }
                }
                
                inStructure =
                        (inOpenElement || inCloseElement || inComment || inCdata);
                
                
                while (!inStructure) {
                    // We found a '<', but it cannot be considered a tag because it is not
                    // the beginning of any known structure
                    
                    locator.countChar(buffer[tagStart]);
                    tagStart = MarkupParsingUtil.findNext(buffer, tagStart + 1, maxi, '<', false, locator);
                    
                    if (tagStart == -1) {
                        return new BufferParseResult(current, currentLine, currentCol, false);
                    }

                    inOpenElement = MarkupParsingUtil.isOpenElementStart(buffer, tagStart, maxi);
                    if (!inOpenElement) {
                        inCloseElement = MarkupParsingUtil.isCloseElementStart(buffer, tagStart, maxi);
                        if (!inCloseElement) {
                            inComment = MarkupParsingUtil.isCommentStart(buffer, tagStart, maxi);
                            if (!inComment) {
                                inCdata = MarkupParsingUtil.isCdataStart(buffer, tagStart, maxi);
                            }
                        }
                    }
                    
                    inStructure =
                            (inOpenElement || inCloseElement || inComment || inCdata);
                
                }
            
                
                if (tagStart > current) {
                    // We avoid empty-string text events
                    handler.text(
                            buffer, current, (tagStart - current), 
                            currentLine, currentCol);
                }
                
                current = tagStart;
                i = current;
                
            } else {
                        
                final boolean avoidQuotes =
                        (inOpenElement || inCloseElement);
                
                tagEnd = MarkupParsingUtil.findNext(buffer, i, maxi, '>', avoidQuotes, locator);
                
                if (tagEnd == -1) {
                    // This is an unfinished structure
                    return new BufferParseResult(current, currentLine, currentCol, true);
                }

                
                if (inOpenElement) {
                    // This is a closing tag
                    
                    handler.structure(buffer, current, (tagEnd - current) + 1, currentLine, currentCol);
                    inOpenElement = false;
                    
                } else if (inCloseElement) {
                    // This is a closing tag
                    
                    handler.structure(buffer, current, (tagEnd - current) + 1, currentLine, currentCol);
                    inCloseElement = false;
                    
                } else if (inComment) {
                    // This is a comment! (obviously ;-))
                    
                    while (tagEnd - current < 7 || buffer[tagEnd - 1] != '-' || buffer[tagEnd - 2] != '-') {
                        // the '>' we chose is not the comment-closing one. Let's find again
                        
                        locator.countChar(buffer[tagEnd]);
                        tagEnd = MarkupParsingUtil.findNext(buffer, tagEnd + 1, maxi, '>', false, locator);
                        
                        if (tagEnd == -1) {
                            return new BufferParseResult(current, currentLine, currentCol, true);
                        }
                        
                    }
                    
                    handler.structure(buffer, current, (tagEnd - current) + 1, currentLine, currentCol);
                    inComment = false;
                    
                } else if (inCdata) {
                    // This is a CDATA section
                    
                    while (tagEnd - current < 12 || buffer[tagEnd - 1] != ']' || buffer[tagEnd - 2] != ']') {
                        // the '>' we chose is not the comment-closing one. Let's find again
                        
                        locator.countChar(buffer[tagEnd]);
                        tagEnd = MarkupParsingUtil.findNext(buffer, tagEnd + 1, maxi, '>', false, locator);
                        
                        if (tagEnd == -1) {
                            return new BufferParseResult(current, currentLine, currentCol, true);
                        }
                        
                    }
                    
                    handler.structure(buffer, current, (tagEnd - current) + 1, currentLine, currentCol);
                    inCdata = false;
                    
                } else {

                    throw new IllegalStateException(
                            "Illegal parsing state: structure is not of a recognized type");
                    
                }
                
                // The '>' char will be considered as processed too
                locator.countChar(buffer[tagEnd]);
                
                current = tagEnd + 1;
                i = current;
                
            }
            
        }
        
        return new BufferParseResult(current, locator.getLine(), locator.getCol(), false);
        
    }
    
    
}
