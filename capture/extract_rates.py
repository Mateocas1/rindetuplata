"""Extract the text snippets that announce a TNA rate from an HTML page.

Reads HTML on stdin and prints a JSON array of unique snippets, in page order. Script and style
contents are ignored; block-level elements split the text into snippets so each one keeps its
own condition (for example "Si tenés entre $10.000 y $1.000.000 ... rinden 19% TNA").
"""

import json
import re
import sys
from html.parser import HTMLParser

BLOCK_TAGS = {
    "address", "article", "aside", "blockquote", "br", "dd", "div", "dl", "dt", "footer",
    "form", "h1", "h2", "h3", "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol",
    "p", "section", "table", "td", "th", "tr", "ul",
}
SKIPPED_TAGS = {"script", "style", "noscript", "template", "svg"}
RATE = re.compile(r"\d{1,3}(?:[.,]\d+)?\s*%\s*TNA", re.IGNORECASE)


class SnippetParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.snippets = []
        self.current = []
        self.skip_depth = 0

    def flush(self):
        text = " ".join("".join(self.current).split())
        if text:
            self.snippets.append(text)
        self.current = []

    def handle_starttag(self, tag, attrs):
        if tag in SKIPPED_TAGS:
            self.skip_depth += 1
        elif tag in BLOCK_TAGS:
            self.flush()

    def handle_endtag(self, tag):
        if tag in SKIPPED_TAGS:
            self.skip_depth = max(0, self.skip_depth - 1)
        elif tag in BLOCK_TAGS:
            self.flush()

    def handle_data(self, data):
        if self.skip_depth == 0:
            self.current.append(data)


def main():
    parser = SnippetParser()
    parser.feed(sys.stdin.read())
    parser.close()
    parser.flush()
    seen = set()
    matches = []
    for snippet in parser.snippets:
        if RATE.search(snippet) and snippet not in seen:
            seen.add(snippet)
            matches.append(snippet)
    json.dump(matches, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    main()
