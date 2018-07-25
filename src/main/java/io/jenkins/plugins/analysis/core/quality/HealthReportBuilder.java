package io.jenkins.plugins.analysis.core.quality;

import java.util.Map;

import com.google.errorprone.annotations.CheckReturnValue;

import edu.hm.hafner.analysis.Priority;
import edu.hm.hafner.analysis.Severity;
import io.jenkins.plugins.analysis.core.model.StaticAnalysisLabelProvider;

import hudson.model.HealthReport;

/**
 * Creates a health report for integer values based on healthy and unhealthy thresholds.
 *
 * @author Ulli Hafner
 * @see HealthReport
 */
public class HealthReportBuilder {
    /**
     * Computes the healthiness of a build based on the specified results. Reports a health of 100% when the specified
     * counter is less than {@link HealthDescriptor#getHealthy()}. Reports a health of 0% when the specified counter is
     * greater than {@link HealthDescriptor#getUnHealthy()}. The computation takes only annotations of the specified
     * severity into account.
     *
     * @param healthDescriptor
     *         health report configuration
     * @param labelProvider
     *         label provider to get the messages from
     * @param sizePerSeverity
     *         number of issues per severity
     *
     * @return the healthiness of a build
     */
    @CheckReturnValue
    public HealthReport computeHealth(final HealthDescriptor healthDescriptor,
            final StaticAnalysisLabelProvider labelProvider,
            final Map<Severity, Integer> sizePerSeverity) {
        int relevantIssuesSize = 0;
        for (Priority priority : Priority.collectPrioritiesFrom(healthDescriptor.getMinimumPriority())) {
            relevantIssuesSize += sizePerSeverity.getOrDefault(Severity.valueOf(priority), 0);
        }
        relevantIssuesSize += sizePerSeverity.getOrDefault(Severity.ERROR, 0);

        if (healthDescriptor.isValid()) {
            int percentage;
            int healthy = healthDescriptor.getHealthy();
            if (relevantIssuesSize < healthy) {
                percentage = 100;
            }
            else {
                int unHealthy = healthDescriptor.getUnHealthy();
                if (relevantIssuesSize > unHealthy) {
                    percentage = 0;
                }
                else {
                    percentage = 100 - ((relevantIssuesSize - healthy) * 100 / (unHealthy - healthy));
                }
            }

            return new HealthReport(percentage, labelProvider.getToolTipLocalizable(relevantIssuesSize));
        }
        return null;
    }
}

