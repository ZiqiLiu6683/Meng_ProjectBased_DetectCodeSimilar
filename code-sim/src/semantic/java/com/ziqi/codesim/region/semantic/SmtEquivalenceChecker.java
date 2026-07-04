package com.ziqi.codesim.region.semantic;

import com.ziqi.codesim.region.semantic.SymbolicExpression.BinaryOperation;
import com.ziqi.codesim.region.semantic.SymbolicExpression.Constant;
import com.ziqi.codesim.region.semantic.SymbolicExpression.Parameter;
import com.ziqi.codesim.region.semantic.SymbolicExpression.RegionInput;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.SolverContextFactory.Solvers;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.FormulaManager;
import org.sosy_lab.java_smt.api.IntegerFormulaManager;
import org.sosy_lab.java_smt.api.NumeralFormula.IntegerFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext;
import org.sosy_lab.java_smt.api.SolverException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Decides whether two {@link SymbolicExpression} summaries compute the same value for all inputs,
 * by asking an SMT solver whether they can ever differ. Parameters are matched by position (the
 * same position on both sides becomes the same SMT variable), so this reaches Type-4 clones that
 * structural matching misses (e.g. {@code x*2} vs {@code y+y}).
 *
 * <p>Uses SMTInterpol (pure Java, integer arithmetic). Cases beyond it degrade safely:
 * {@link SymbolicExpression.Unknown} summaries and solver-undecidable goals (e.g. nonlinear
 * {@code var*var}) return {@link EquivalenceVerdict#UNKNOWN}; bitwise/shift operators return
 * {@link EquivalenceVerdict#UNSUPPORTED}. A clean YES/NO is only ever EQUIVALENT/DIFFERENT.
 */
public final class SmtEquivalenceChecker {

    // One SMTInterpol context per checker instance, created lazily and reused across every check()
    // (a batch caller keeps a single checker, so all its checks share it). SMTInterpol is pure Java,
    // so holding the context open leaks no native resource; each check still uses a fresh prover.
    private SolverContext context;
    private IntegerFormulaManager imgr;
    private BooleanFormulaManager bmgr;

    public EquivalenceVerdict check(SymbolicExpression left, SymbolicExpression right) {
        if (left.hasUnknown() || right.hasUnknown()) {
            return EquivalenceVerdict.UNKNOWN;
        }
        try {
            ensureContext();
            Map<Integer, IntegerFormula> variables = new HashMap<>();
            IntegerFormula leftFormula = translate(left, imgr, variables);
            IntegerFormula rightFormula = translate(right, imgr, variables);
            BooleanFormula canDiffer = bmgr.not(imgr.equal(leftFormula, rightFormula));

            try (ProverEnvironment prover = context.newProverEnvironment()) {
                prover.addConstraint(canDiffer);
                return prover.isUnsat() ? EquivalenceVerdict.EQUIVALENT : EquivalenceVerdict.DIFFERENT;
            }
        } catch (UnsupportedOperationException ex) {
            return EquivalenceVerdict.UNSUPPORTED;
        } catch (SolverException ex) {
            // Solver could not decide (e.g. nonlinear arithmetic under SMTInterpol).
            return EquivalenceVerdict.UNKNOWN;
        } catch (Exception ex) {
            return EquivalenceVerdict.UNKNOWN;
        }
    }

    /**
     * Checks whether two REGION output expressions (over {@link RegionInput}s) are equal for all
     * inputs, with the two regions' inputs matched: a right input aligned to a left input becomes
     * the same solver variable; an unaligned right input is a distinct variable.
     *
     * @param rightInputToLeft right region input SSA value -> the left input it is aligned to
     */
    public EquivalenceVerdict checkRegionOutputs(SymbolicExpression left, SymbolicExpression right,
                                                 Map<Integer, Integer> rightInputToLeft) {
        if (left.hasUnknown() || right.hasUnknown()) {
            return EquivalenceVerdict.UNKNOWN;
        }
        try {
            ensureContext();
            IntegerFormula leftFormula = translateRegion(left, valueId -> "v" + valueId);
            IntegerFormula rightFormula = translateRegion(right, valueId ->
                    rightInputToLeft.containsKey(valueId) ? "v" + rightInputToLeft.get(valueId) : "r" + valueId);
            BooleanFormula canDiffer = bmgr.not(imgr.equal(leftFormula, rightFormula));
            try (ProverEnvironment prover = context.newProverEnvironment()) {
                prover.addConstraint(canDiffer);
                return prover.isUnsat() ? EquivalenceVerdict.EQUIVALENT : EquivalenceVerdict.DIFFERENT;
            }
        } catch (UnsupportedOperationException ex) {
            return EquivalenceVerdict.UNSUPPORTED;
        } catch (SolverException ex) {
            return EquivalenceVerdict.UNKNOWN;
        } catch (Exception ex) {
            return EquivalenceVerdict.UNKNOWN;
        }
    }

    private IntegerFormula translateRegion(SymbolicExpression expression, Function<Integer, String> nameOf) {
        if (expression instanceof Constant constant) {
            return imgr.makeNumber(constant.value());
        }
        if (expression instanceof RegionInput input) {
            return imgr.makeVariable(nameOf.apply(input.valueId()));
        }
        if (expression instanceof BinaryOperation operation) {
            IntegerFormula left = translateRegion(operation.left(), nameOf);
            IntegerFormula right = translateRegion(operation.right(), nameOf);
            return switch (operation.operator()) {
                case "add" -> imgr.add(left, right);
                case "sub" -> imgr.subtract(left, right);
                case "mul" -> imgr.multiply(left, right);
                case "div" -> imgr.divide(left, right);
                case "rem" -> imgr.modulo(left, right);
                default -> throw new UnsupportedOperationException("operator not modelled: " + operation.operator());
            };
        }
        throw new UnsupportedOperationException("cannot translate region expression: " + expression);
    }

    private void ensureContext() throws Exception {
        if (context == null) {
            Configuration config = Configuration.defaultConfiguration();
            LogManager logger = BasicLogManager.create(config);
            ShutdownManager shutdown = ShutdownManager.create();
            context = SolverContextFactory.createSolverContext(
                    config, logger, shutdown.getNotifier(), Solvers.SMTINTERPOL);
            FormulaManager fmgr = context.getFormulaManager();
            imgr = fmgr.getIntegerFormulaManager();
            bmgr = fmgr.getBooleanFormulaManager();
        }
    }

    private static IntegerFormula translate(SymbolicExpression expression,
                                            IntegerFormulaManager imgr,
                                            Map<Integer, IntegerFormula> variables) {
        if (expression instanceof Constant constant) {
            return imgr.makeNumber(constant.value());
        }
        if (expression instanceof Parameter parameter) {
            return variables.computeIfAbsent(parameter.index(), i -> imgr.makeVariable("p" + i));
        }
        if (expression instanceof BinaryOperation operation) {
            IntegerFormula left = translate(operation.left(), imgr, variables);
            IntegerFormula right = translate(operation.right(), imgr, variables);
            return switch (operation.operator()) {
                case "add" -> imgr.add(left, right);
                case "sub" -> imgr.subtract(left, right);
                case "mul" -> imgr.multiply(left, right);
                case "div" -> imgr.divide(left, right);
                case "rem" -> imgr.modulo(left, right);
                default -> throw new UnsupportedOperationException("operator not modelled: " + operation.operator());
            };
        }
        // Unknown is filtered out before translation; reaching here is a programming error.
        throw new UnsupportedOperationException("cannot translate: " + expression);
    }
}
