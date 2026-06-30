package com.ziqi.codesim.region.semantic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Environment probe for Phase B: confirms JavaSMT can create a solver context and decide a trivial
 * integer identity (2*x == x+x). Uses SMTInterpol, a pure-Java solver (no native library), so it
 * works on macOS arm64 where JavaSMT ships no signed Z3 binary.
 */
@EnabledIfSystemProperty(named = "semantic.tests.enabled", matches = "true")
class SmtSmokeTest {

    @Test
    void solverProvesTrivialIntegerIdentity() throws Exception {
        Configuration config = Configuration.defaultConfiguration();
        LogManager logger = BasicLogManager.create(config);
        ShutdownManager shutdown = ShutdownManager.create();

        try (SolverContext context = SolverContextFactory.createSolverContext(
                config, logger, shutdown.getNotifier(), Solvers.SMTINTERPOL)) {
            FormulaManager fmgr = context.getFormulaManager();
            IntegerFormulaManager imgr = fmgr.getIntegerFormulaManager();
            BooleanFormulaManager bmgr = fmgr.getBooleanFormulaManager();

            IntegerFormula x = imgr.makeVariable("x");
            // 2*x == x + x  is valid iff its negation is unsatisfiable.
            BooleanFormula identity = imgr.equal(imgr.multiply(imgr.makeNumber(2), x), imgr.add(x, x));

            try (ProverEnvironment prover = context.newProverEnvironment()) {
                prover.addConstraint(bmgr.not(identity));
                assertTrue(prover.isUnsat(), "2*x == x+x must be valid (its negation UNSAT)");
            }
        }
    }
}
