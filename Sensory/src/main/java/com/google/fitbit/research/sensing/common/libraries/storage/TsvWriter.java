
package com.google.fitbit.research.sensing.common.libraries.storage;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collector;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
/** Defines the schema for writing events as TSV files. */
@CheckReturnValue // see go/why-crv
public interface TsvWriter<O> {
  /** Returns the column names for the TSV header. */
  ImmutableList<String> header();
  /**
   * Converts an event into a series of row values.
   *
   * <p>If the number of values in a row exceeds the number of columns specified in {@link
   * #rowWrap}, the remaining values will be wrapped to subsequent rows.
   */
  ImmutableList<String> row(O value);
  default int rowWrap() {
    return this.header().size();
  }
  default void writeToFile(ImmutableList<? extends O> outputs, File outputFile) throws IOException {
    write(this, outputs, outputFile);
  }
  /** Extracts a specific column value from the event. */
  public static interface Column<O> extends Function<O, Object> {}
  /**
   * Writes a list of items to the specified TSV file. This is a blocking operation and should not
   * be directly called from the UI thread.
   */
  public static <O> void write(
      TsvWriter<O> tsv, ImmutableList<? extends O> outputs, File outputFile) throws IOException {
    outputFile.getParentFile().mkdirs();
    try (CSVPrinter printer =
        new CSVPrinter(FilesCompat.newBufferedWriter(outputFile), tsvFormat())) {
      printer.printRecord(tsv.header());
      for (O output : outputs) {
        ImmutableList<String> row = tsv.row(output);
        for (List<String> rowPart : Iterables.partition(row, tsv.rowWrap())) {
          printer.printRecord(rowPart);
        }
      }
    }
  }
  static CSVFormat tsvFormat() {
    return CSVFormat.Builder.create().setDelimiter('\t').setRecordSeparator('\n').build();
  }
  public static <T> Builder<T> builder() {
    return new Builder<T>();
  }
  /** Builder for {@link TsvWriter}s that create one row per event. */
  public static class Builder<T> {
    private static final Collector<String, ?, ImmutableList<String>> COLLECT_IMMUTABLE_STRING_LIST =
        Collector.of(
            ImmutableList::<String>builder,
            ImmutableList.Builder::<String>add,
            (a, b) -> a.addAll(b.build()),
            ImmutableList.Builder::<String>build);
    private final ImmutableMap.Builder<String, Column<T>> columnsBuilder = ImmutableMap.builder();
    protected Builder() {}
    @CanIgnoreReturnValue
    public Builder<T> addColumn(String name, Column<T> col) {
      columnsBuilder.put(name, col);
      return this;
    }
    public TsvWriter<T> build() {
      ImmutableMap<String, Column<T>> columns = columnsBuilder.buildOrThrow();
      return new TsvWriter<>() {
        @Override
        public ImmutableList<String> header() {
          return columns.keySet().asList();
        }
        @Override
        public ImmutableList<String> row(T value) {
          return columns.values().stream()
              .map(
                  colFn -> {
                    Object result = colFn.apply(value);
                    return result == null ? "" : result.toString();
                  })
              .collect(COLLECT_IMMUTABLE_STRING_LIST);
        }
      };
    }
  }
}
