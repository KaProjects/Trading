/**
 * Matches the innermost element whose whole text is `text`, even when the text is split across
 * child elements - which it is wherever a figure's leading plus is set in its own span so it can
 * be pulled against the digits.
 */
export const matchText = text => (content, element) =>
    element?.textContent === text
    && !Array.from(element.children).some(child => child.textContent === text);
