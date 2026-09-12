import {roundedSuggestion} from "../PeriodFinancialFields";

describe("roundedSuggestion", () => {
    test("rounds a suggestion to the allowed decimal places", () => {
        expect(roundedSuggestion("1061.280075", 2)).toBe("1061.28");
        expect(roundedSuggestion("305.836238", 2)).toBe("305.84");
        expect(roundedSuggestion("1436.491616", 2)).toBe("1436.49");
    });

    test("keeps values that already fit", () => {
        expect(roundedSuggestion("9326.5", 2)).toBe("9326.5");
        expect(roundedSuggestion("393", 2)).toBe("393");
    });

    test("drops trailing zeros introduced by rounding", () => {
        expect(roundedSuggestion("1740.001", 2)).toBe("1740");
        expect(roundedSuggestion("12.30400", 2)).toBe("12.3");
    });

    test("respects a four decimal constraint", () => {
        expect(roundedSuggestion("1165.123456", 4)).toBe("1165.1235");
    });

    test("keeps negative values negative", () => {
        expect(roundedSuggestion("-305.836238", 2)).toBe("-305.84");
    });

    test("passes through values it cannot round", () => {
        expect(roundedSuggestion(null, 2)).toBeNull();
        expect(roundedSuggestion(undefined, 2)).toBeUndefined();
        expect(roundedSuggestion("", 2)).toBe("");
        expect(roundedSuggestion("abc", 2)).toBe("abc");
        expect(roundedSuggestion("12.345", undefined)).toBe("12.345");
    });
});
