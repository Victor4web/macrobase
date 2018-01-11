/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.stanford.futuredata.macrobase.sql;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static edu.stanford.futuredata.macrobase.sql.SqlFormatter.formatSql;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.stanford.futuredata.macrobase.sql.tree.AllColumns;
import edu.stanford.futuredata.macrobase.sql.tree.ArithmeticBinaryExpression;
import edu.stanford.futuredata.macrobase.sql.tree.ArithmeticUnaryExpression;
import edu.stanford.futuredata.macrobase.sql.tree.ArrayConstructor;
import edu.stanford.futuredata.macrobase.sql.tree.AstVisitor;
import edu.stanford.futuredata.macrobase.sql.tree.AtTimeZone;
import edu.stanford.futuredata.macrobase.sql.tree.BetweenPredicate;
import edu.stanford.futuredata.macrobase.sql.tree.BinaryLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.BindExpression;
import edu.stanford.futuredata.macrobase.sql.tree.BooleanLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.Cast;
import edu.stanford.futuredata.macrobase.sql.tree.CharLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.CoalesceExpression;
import edu.stanford.futuredata.macrobase.sql.tree.ComparisonExpression;
import edu.stanford.futuredata.macrobase.sql.tree.Cube;
import edu.stanford.futuredata.macrobase.sql.tree.DecimalLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.DereferenceExpression;
import edu.stanford.futuredata.macrobase.sql.tree.DoubleLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.ExistsPredicate;
import edu.stanford.futuredata.macrobase.sql.tree.Expression;
import edu.stanford.futuredata.macrobase.sql.tree.Extract;
import edu.stanford.futuredata.macrobase.sql.tree.FieldReference;
import edu.stanford.futuredata.macrobase.sql.tree.FrameBound;
import edu.stanford.futuredata.macrobase.sql.tree.FunctionCall;
import edu.stanford.futuredata.macrobase.sql.tree.GenericLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.GroupingElement;
import edu.stanford.futuredata.macrobase.sql.tree.GroupingOperation;
import edu.stanford.futuredata.macrobase.sql.tree.GroupingSets;
import edu.stanford.futuredata.macrobase.sql.tree.Identifier;
import edu.stanford.futuredata.macrobase.sql.tree.IfExpression;
import edu.stanford.futuredata.macrobase.sql.tree.InListExpression;
import edu.stanford.futuredata.macrobase.sql.tree.InPredicate;
import edu.stanford.futuredata.macrobase.sql.tree.IntervalLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.IsNotNullPredicate;
import edu.stanford.futuredata.macrobase.sql.tree.IsNullPredicate;
import edu.stanford.futuredata.macrobase.sql.tree.LambdaArgumentDeclaration;
import edu.stanford.futuredata.macrobase.sql.tree.LambdaExpression;
import edu.stanford.futuredata.macrobase.sql.tree.LikePredicate;
import edu.stanford.futuredata.macrobase.sql.tree.LogicalBinaryExpression;
import edu.stanford.futuredata.macrobase.sql.tree.LongLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.Node;
import edu.stanford.futuredata.macrobase.sql.tree.NotExpression;
import edu.stanford.futuredata.macrobase.sql.tree.NullIfExpression;
import edu.stanford.futuredata.macrobase.sql.tree.NullLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.OrderBy;
import edu.stanford.futuredata.macrobase.sql.tree.Parameter;
import edu.stanford.futuredata.macrobase.sql.tree.QualifiedName;
import edu.stanford.futuredata.macrobase.sql.tree.QuantifiedComparisonExpression;
import edu.stanford.futuredata.macrobase.sql.tree.RatioMetricExpression;
import edu.stanford.futuredata.macrobase.sql.tree.Rollup;
import edu.stanford.futuredata.macrobase.sql.tree.Row;
import edu.stanford.futuredata.macrobase.sql.tree.SearchedCaseExpression;
import edu.stanford.futuredata.macrobase.sql.tree.SimpleCaseExpression;
import edu.stanford.futuredata.macrobase.sql.tree.SimpleGroupBy;
import edu.stanford.futuredata.macrobase.sql.tree.SortItem;
import edu.stanford.futuredata.macrobase.sql.tree.StringLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.SubqueryExpression;
import edu.stanford.futuredata.macrobase.sql.tree.SubscriptExpression;
import edu.stanford.futuredata.macrobase.sql.tree.TimeLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.TimestampLiteral;
import edu.stanford.futuredata.macrobase.sql.tree.TryExpression;
import edu.stanford.futuredata.macrobase.sql.tree.WhenClause;
import edu.stanford.futuredata.macrobase.sql.tree.Window;
import edu.stanford.futuredata.macrobase.sql.tree.WindowFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Function;

public final class ExpressionFormatter {

  private ExpressionFormatter() {
  }

  public static String formatExpression(Expression expression,
      Optional<List<Expression>> parameters) {
    return new Formatter(parameters).process(expression, null);
  }

  public static class Formatter
      extends AstVisitor<String, Void> {

    private final Optional<List<Expression>> parameters;

    public Formatter(Optional<List<Expression>> parameters) {
      this.parameters = parameters;
    }

    @Override
    protected String visitNode(Node node, Void context) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected String visitRow(Row node, Void context) {
      return "ROW (" + Joiner.on(", ").join(node.getItems().stream()
          .map((child) -> process(child, context))
          .collect(toList())) + ")";
    }

    @Override
    protected String visitExpression(Expression node, Void context) {
      throw new UnsupportedOperationException(
          format("not yet implemented: %s.visit%s", getClass().getName(),
              node.getClass().getSimpleName()));
    }

    @Override
    protected String visitAtTimeZone(AtTimeZone node, Void context) {
      return new StringBuilder()
          .append(process(node.getValue(), context))
          .append(" AT TIME ZONE ")
          .append(process(node.getTimeZone(), context)).toString();
    }

    @Override
    protected String visitExtract(Extract node, Void context) {
      return "EXTRACT(" + node.getField() + " FROM " + process(node.getExpression(), context) + ")";
    }

    @Override
    protected String visitBooleanLiteral(BooleanLiteral node, Void context) {
      return String.valueOf(node.getValue());
    }

    @Override
    protected String visitStringLiteral(StringLiteral node, Void context) {
      return formatStringLiteral(node.getValue());
    }

    @Override
    protected String visitCharLiteral(CharLiteral node, Void context) {
      return "CHAR " + formatStringLiteral(node.getValue());
    }

    @Override
    protected String visitBinaryLiteral(BinaryLiteral node, Void context) {
      return "X'" + node.toHexString() + "'";
    }

    @Override
    protected String visitParameter(Parameter node, Void context) {
      if (parameters.isPresent()) {
        checkArgument(node.getPosition() < parameters.get().size(),
            "Invalid parameter number %s.  Max value is %s", node.getPosition(),
            parameters.get().size() - 1);
        return process(parameters.get().get(node.getPosition()), context);
      }
      return "?";
    }

    @Override
    protected String visitArrayConstructor(ArrayConstructor node, Void context) {
      ImmutableList.Builder<String> valueStrings = ImmutableList.builder();
      for (Expression value : node.getValues()) {
        valueStrings.add(formatSql(value, parameters));
      }
      return "ARRAY[" + Joiner.on(",").join(valueStrings.build()) + "]";
    }

    @Override
    protected String visitSubscriptExpression(SubscriptExpression node, Void context) {
      return formatSql(node.getBase(), parameters) + "[" + formatSql(node.getIndex(), parameters)
          + "]";
    }

    @Override
    protected String visitLongLiteral(LongLiteral node, Void context) {
      return Long.toString(node.getValue());
    }

    @Override
    protected String visitDoubleLiteral(DoubleLiteral node, Void context) {
      return Double.toString(node.getValue());
    }

    @Override
    protected String visitDecimalLiteral(DecimalLiteral node, Void context) {
      return "DECIMAL '" + node.getValue() + "'";
    }

    @Override
    protected String visitGenericLiteral(GenericLiteral node, Void context) {
      return node.getType() + " " + formatStringLiteral(node.getValue());
    }

    @Override
    protected String visitTimeLiteral(TimeLiteral node, Void context) {
      return "TIME '" + node.getValue() + "'";
    }

    @Override
    protected String visitTimestampLiteral(TimestampLiteral node, Void context) {
      return "TIMESTAMP '" + node.getValue() + "'";
    }

    @Override
    protected String visitNullLiteral(NullLiteral node, Void context) {
      return "null";
    }

    @Override
    protected String visitIntervalLiteral(IntervalLiteral node, Void context) {
      String sign = (node.getSign() == IntervalLiteral.Sign.NEGATIVE) ? "- " : "";
      StringBuilder builder = new StringBuilder()
          .append("INTERVAL ")
          .append(sign)
          .append(" '").append(node.getValue()).append("' ")
          .append(node.getStartField());

      if (node.getEndField().isPresent()) {
        builder.append(" TO ").append(node.getEndField().get());
      }
      return builder.toString();
    }

    @Override
    protected String visitSubqueryExpression(SubqueryExpression node, Void context) {
      return "(" + formatSql(node.getQuery(), parameters) + ")";
    }

    @Override
    protected String visitExists(ExistsPredicate node, Void context) {
      return "(EXISTS " + formatSql(node.getSubquery(), parameters) + ")";
    }

    @Override
    protected String visitIdentifier(Identifier node, Void context) {
      if (!node.isDelimited()) {
        return node.getValue();
      } else {
        return '"' + node.getValue().replace("\"", "\"\"") + '"';
      }
    }

    @Override
    protected String visitLambdaArgumentDeclaration(LambdaArgumentDeclaration node, Void context) {
      return formatExpression(node.getName(), parameters);
    }

    @Override
    protected String visitDereferenceExpression(DereferenceExpression node, Void context) {
      String baseString = process(node.getBase(), context);
      return baseString + "." + process(node.getField());
    }

    private static String formatQualifiedName(QualifiedName name) {
      List<String> parts = new ArrayList<>();
      for (String part : name.getParts()) {
        parts.add(formatIdentifier(part));
      }
      return Joiner.on('.').join(parts);
    }

    @Override
    public String visitFieldReference(FieldReference node, Void context) {
      // add colon so this won't parse
      return ":input(" + node.getFieldIndex() + ")";
    }

    @Override
    protected String visitFunctionCall(FunctionCall node, Void context) {
      StringBuilder builder = new StringBuilder();

      String arguments = joinExpressions(node.getArguments());
      if (node.getArguments().isEmpty() && "count".equalsIgnoreCase(node.getName().getSuffix())) {
        arguments = "*";
      }
      if (node.isDistinct()) {
        arguments = "DISTINCT " + arguments;
      }

      builder.append(formatQualifiedName(node.getName()))
          .append('(').append(arguments).append(')');

      if (node.getFilter().isPresent()) {
        builder.append(" FILTER ").append(visitFilter(node.getFilter().get(), context));
      }

      if (node.getWindow().isPresent()) {
        builder.append(" OVER ").append(visitWindow(node.getWindow().get(), context));
      }

      return builder.toString();
    }

    @Override
    protected String visitLambdaExpression(LambdaExpression node, Void context) {
      StringBuilder builder = new StringBuilder();

      builder.append('(');
      Joiner.on(", ").appendTo(builder, node.getArguments());
      builder.append(") -> ");
      builder.append(process(node.getBody(), context));
      return builder.toString();
    }

    @Override
    protected String visitBindExpression(BindExpression node, Void context) {
      StringBuilder builder = new StringBuilder();

      builder.append("\"$INTERNAL$BIND\"(");
      for (Expression value : node.getValues()) {
        builder.append(process(value, context) + ", ");
      }
      builder.append(process(node.getFunction(), context) + ")");
      return builder.toString();
    }

    @Override
    protected String visitLogicalBinaryExpression(LogicalBinaryExpression node, Void context) {
      return formatBinaryExpression(node.getType().toString(), node.getLeft(), node.getRight());
    }

    @Override
    protected String visitNotExpression(NotExpression node, Void context) {
      return "(NOT " + process(node.getValue(), context) + ")";
    }

    @Override
    protected String visitComparisonExpression(ComparisonExpression node, Void context) {
      return formatBinaryExpression(node.getType().getValue(), node.getLeft(), node.getRight());
    }

    @Override
    protected String visitIsNullPredicate(IsNullPredicate node, Void context) {
      return "(" + process(node.getValue(), context) + " IS NULL)";
    }

    @Override
    protected String visitIsNotNullPredicate(IsNotNullPredicate node, Void context) {
      return "(" + process(node.getValue(), context) + " IS NOT NULL)";
    }

    @Override
    protected String visitNullIfExpression(NullIfExpression node, Void context) {
      return "NULLIF(" + process(node.getFirst(), context) + ", " + process(node.getSecond(),
          context) + ')';
    }

    @Override
    protected String visitIfExpression(IfExpression node, Void context) {
      StringBuilder builder = new StringBuilder();
      builder.append("IF(")
          .append(process(node.getCondition(), context))
          .append(", ")
          .append(process(node.getTrueValue(), context));
      if (node.getFalseValue().isPresent()) {
        builder.append(", ")
            .append(process(node.getFalseValue().get(), context));
      }
      builder.append(")");
      return builder.toString();
    }

    @Override
    protected String visitTryExpression(TryExpression node, Void context) {
      return "TRY(" + process(node.getInnerExpression(), context) + ")";
    }

    @Override
    protected String visitCoalesceExpression(CoalesceExpression node, Void context) {
      return "COALESCE(" + joinExpressions(node.getOperands()) + ")";
    }

    @Override
    protected String visitArithmeticUnary(ArithmeticUnaryExpression node, Void context) {
      String value = process(node.getValue(), context);

      switch (node.getSign()) {
        case MINUS:
          // this is to avoid turning a sequence of "-" into a comment (i.e., "-- comment")
          String separator = value.startsWith("-") ? " " : "";
          return "-" + separator + value;
        case PLUS:
          return "+" + value;
        default:
          throw new UnsupportedOperationException("Unsupported sign: " + node.getSign());
      }
    }

    @Override
    protected String visitArithmeticBinary(ArithmeticBinaryExpression node, Void context) {
      return formatBinaryExpression(node.getType().getValue(), node.getLeft(), node.getRight());
    }

    @Override
    protected String visitLikePredicate(LikePredicate node, Void context) {
      StringBuilder builder = new StringBuilder();

      builder.append('(')
          .append(process(node.getValue(), context))
          .append(" LIKE ")
          .append(process(node.getPattern(), context));

      if (node.getEscape() != null) {
        builder.append(" ESCAPE ")
            .append(process(node.getEscape(), context));
      }

      builder.append(')');

      return builder.toString();
    }

    @Override
    protected String visitAllColumns(AllColumns node, Void context) {
      if (node.getPrefix().isPresent()) {
        return node.getPrefix().get() + ".*";
      }

      return "*";
    }

    @Override
    public String visitCast(Cast node, Void context) {
      return (node.isSafe() ? "TRY_CAST" : "CAST") +
          "(" + process(node.getExpression(), context) + " AS " + node.getType() + ")";
    }

    @Override
    protected String visitSearchedCaseExpression(SearchedCaseExpression node, Void context) {
      ImmutableList.Builder<String> parts = ImmutableList.builder();
      parts.add("CASE");
      for (WhenClause whenClause : node.getWhenClauses()) {
        parts.add(process(whenClause, context));
      }

      node.getDefaultValue()
          .ifPresent((value) -> parts.add("ELSE").add(process(value, context)));

      parts.add("END");

      return "(" + Joiner.on(' ').join(parts.build()) + ")";
    }

    @Override
    protected String visitSimpleCaseExpression(SimpleCaseExpression node, Void context) {
      ImmutableList.Builder<String> parts = ImmutableList.builder();

      parts.add("CASE")
          .add(process(node.getOperand(), context));

      for (WhenClause whenClause : node.getWhenClauses()) {
        parts.add(process(whenClause, context));
      }

      node.getDefaultValue()
          .ifPresent((value) -> parts.add("ELSE").add(process(value, context)));

      parts.add("END");

      return "(" + Joiner.on(' ').join(parts.build()) + ")";
    }

    @Override
    protected String visitWhenClause(WhenClause node, Void context) {
      return "WHEN " + process(node.getOperand(), context) + " THEN " + process(node.getResult(),
          context);
    }

    @Override
    protected String visitBetweenPredicate(BetweenPredicate node, Void context) {
      return "(" + process(node.getValue(), context) + " BETWEEN " +
          process(node.getMin(), context) + " AND " + process(node.getMax(), context) + ")";
    }

    @Override
    protected String visitInPredicate(InPredicate node, Void context) {
      return "(" + process(node.getValue(), context) + " IN " + process(node.getValueList(),
          context) + ")";
    }

    @Override
    protected String visitInListExpression(InListExpression node, Void context) {
      return "(" + joinExpressions(node.getValues()) + ")";
    }

    private String visitFilter(Expression node, Void context) {
      return "(WHERE " + process(node, context) + ')';
    }

    @Override
    public String visitWindow(Window node, Void context) {
      List<String> parts = new ArrayList<>();

      if (!node.getPartitionBy().isEmpty()) {
        parts.add("PARTITION BY " + joinExpressions(node.getPartitionBy()));
      }
      if (node.getOrderBy().isPresent()) {
        parts.add(formatOrderBy(node.getOrderBy().get(), parameters));
      }
      if (node.getFrame().isPresent()) {
        parts.add(process(node.getFrame().get(), context));
      }

      return '(' + Joiner.on(' ').join(parts) + ')';
    }

    @Override
    public String visitWindowFrame(WindowFrame node, Void context) {
      StringBuilder builder = new StringBuilder();

      builder.append(node.getType().toString()).append(' ');

      if (node.getEnd().isPresent()) {
        builder.append("BETWEEN ")
            .append(process(node.getStart(), context))
            .append(" AND ")
            .append(process(node.getEnd().get(), context));
      } else {
        builder.append(process(node.getStart(), context));
      }

      return builder.toString();
    }

    @Override
    public String visitFrameBound(FrameBound node, Void context) {
      switch (node.getType()) {
        case UNBOUNDED_PRECEDING:
          return "UNBOUNDED PRECEDING";
        case PRECEDING:
          return process(node.getValue().get(), context) + " PRECEDING";
        case CURRENT_ROW:
          return "CURRENT ROW";
        case FOLLOWING:
          return process(node.getValue().get(), context) + " FOLLOWING";
        case UNBOUNDED_FOLLOWING:
          return "UNBOUNDED FOLLOWING";
      }
      throw new IllegalArgumentException("unhandled type: " + node.getType());
    }

    @Override
    protected String visitQuantifiedComparisonExpression(QuantifiedComparisonExpression node,
        Void context) {
      return new StringBuilder()
          .append("(")
          .append(process(node.getValue(), context))
          .append(' ')
          .append(node.getComparisonType().getValue())
          .append(' ')
          .append(node.getQuantifier().toString())
          .append(' ')
          .append(process(node.getSubquery(), context))
          .append(")")
          .toString();
    }

    public String visitGroupingOperation(GroupingOperation node, Void context) {
      return "GROUPING (" + joinExpressions(node.getGroupingColumns()) + ")";
    }

    public String visitRatioMetricExpression(RatioMetricExpression node, Void context) {
      return node.getFuncName() + "(" + node.getAggExpr() + "(*))";
    }

    private String formatBinaryExpression(String operator, Expression left, Expression right) {
      return '(' + process(left, null) + ' ' + operator + ' ' + process(right, null) + ')';
    }

    private String joinExpressions(List<Expression> expressions) {
      return Joiner.on(", ").join((Iterable<?>) expressions.stream()
          .map((e) -> process(e, null))
          .iterator());
    }

    private static String formatIdentifier(String s) {
      // TODO: handle escaping properly
      return '"' + s + '"';
    }
  }

  static String formatStringLiteral(String s) {
    s = s.replace("'", "''");
    if (isAsciiPrintable(s)) {
      return "'" + s + "'";
    }

    StringBuilder builder = new StringBuilder();
    builder.append("U&'");
    PrimitiveIterator.OfInt iterator = s.codePoints().iterator();
    while (iterator.hasNext()) {
      int codePoint = iterator.nextInt();
      checkArgument(codePoint >= 0, "Invalid UTF-8 encoding in characters: %s", s);
      if (isAsciiPrintable(codePoint)) {
        char ch = (char) codePoint;
        if (ch == '\\') {
          builder.append(ch);
        }
        builder.append(ch);
      } else if (codePoint <= 0xFFFF) {
        builder.append('\\');
        builder.append(String.format("%04X", codePoint));
      } else {
        builder.append("\\+");
        builder.append(String.format("%06X", codePoint));
      }
    }
    builder.append("'");
    return builder.toString();
  }

  static String formatOrderBy(OrderBy orderBy, Optional<List<Expression>> parameters) {
    return "ORDER BY " + formatSortItems(orderBy.getSortItems(), parameters);
  }

  static String formatSortItems(List<SortItem> sortItems, Optional<List<Expression>> parameters) {
    return Joiner.on(", ").join((Iterable<?>) sortItems.stream()
        .map(sortItemFormatterFunction(parameters))
        .iterator());
  }

  static String formatGroupBy(List<GroupingElement> groupingElements) {
    return formatGroupBy(groupingElements, Optional.empty());
  }

  static String formatGroupBy(List<GroupingElement> groupingElements,
      Optional<List<Expression>> parameters) {
    ImmutableList.Builder<String> resultStrings = ImmutableList.builder();

    for (GroupingElement groupingElement : groupingElements) {
      String result = "";
      if (groupingElement instanceof SimpleGroupBy) {
        Set<Expression> columns = ImmutableSet
            .copyOf(((SimpleGroupBy) groupingElement).getColumnExpressions());
        if (columns.size() == 1) {
          result = formatExpression(getOnlyElement(columns), parameters);
        } else {
          result = formatGroupingSet(columns, parameters);
        }
      } else if (groupingElement instanceof GroupingSets) {
        result = format("GROUPING SETS (%s)", Joiner.on(", ").join(
            (Iterable<?>) ((GroupingSets) groupingElement).getSets().stream()
                .map(ExpressionFormatter::formatGroupingSet)
                .iterator()));
      } else if (groupingElement instanceof Cube) {
        result = format("CUBE %s", formatGroupingSet(((Cube) groupingElement).getColumns()));
      } else if (groupingElement instanceof Rollup) {
        result = format("ROLLUP %s", formatGroupingSet(((Rollup) groupingElement).getColumns()));
      }
      resultStrings.add(result);
    }
    return Joiner.on(", ").join(resultStrings.build());
  }

  private static boolean isAsciiPrintable(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!isAsciiPrintable(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAsciiPrintable(int codePoint) {
    return codePoint < 0x7F && codePoint >= 0x20;
  }

  private static String formatGroupingSet(Set<Expression> groupingSet,
      Optional<List<Expression>> parameters) {
    return format("(%s)", Joiner.on(", ").join((Iterable<?>) groupingSet.stream()
        .map(e -> formatExpression(e, parameters))
        .iterator()));
  }

  private static String formatGroupingSet(List<QualifiedName> groupingSet) {
    return format("(%s)", Joiner.on(", ").join(groupingSet));
  }

  private static Function<SortItem, String> sortItemFormatterFunction(
      Optional<List<Expression>> parameters) {
    return input -> {
      StringBuilder builder = new StringBuilder();

      builder.append(formatExpression(input.getSortKey(), parameters));

      switch (input.getOrdering()) {
        case ASCENDING:
          builder.append(" ASC");
          break;
        case DESCENDING:
          builder.append(" DESC");
          break;
        default:
          throw new UnsupportedOperationException("unknown ordering: " + input.getOrdering());
      }

      switch (input.getNullOrdering()) {
        case FIRST:
          builder.append(" NULLS FIRST");
          break;
        case LAST:
          builder.append(" NULLS LAST");
          break;
        case UNDEFINED:
          // no op
          break;
        default:
          throw new UnsupportedOperationException(
              "unknown null ordering: " + input.getNullOrdering());
      }

      return builder.toString();
    };
  }
}
