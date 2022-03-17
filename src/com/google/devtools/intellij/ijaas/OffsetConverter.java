package com.google.devtools.intellij.ijaas;

import org.eclipse.lsp4j.Position;

public abstract class OffsetConverter {
  private OffsetConverter() {}

  public static int positionToOffset(byte[] bytes, Position pos) {
    int line = 0;
    int col = 0;
    for (int i = 0; i < bytes.length; i++) {
      if (line == pos.getLine() && col == pos.getCharacter()) {
        return i;
      }

      col++;
      if (bytes[i] == '\n') {
        line++;
        col = 0;
      }
    }
    throw new IllegalArgumentException();
  }

  public static Position offsetToPosition(byte[] bytes, int off) {
    if (bytes.length < off) {
      throw new IllegalArgumentException();
    }
    int line = 0;
    int col = 0;
    for (int i = 0; i < off; i++) {
      col++;
      if (bytes[i] == '\n') {
        line++;
        col = 0;
      }
    }
    return new Position(line, col);
  }
}
