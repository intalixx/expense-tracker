package com.intalix.expense.tracker

import org.junit.Assume
import org.junit.Test
import java.io.File

class ClassifierSampleTest {
    @Test fun distributionOnSampleBackup() {
        val path = System.getProperty("mbak.sample").orEmpty()
        Assume.assumeTrue(path.isNotBlank())
        val out = java.util.TreeMap<String, Int>(); val unk = HashMap<String, Int>()
        for (line in File(path).readLines()) {
            val (t, payee) = line.split("	", limit = 2); val type = t.toInt()
            val c = Classifier.classify(type, payee) ?: "(unclassified)"
            out.merge("$type/$c", 1, Int::plus)
            if (c == "(unclassified)") unk.merge(payee, 1, Int::plus)
        }
        File(System.getProperty("java.io.tmpdir"), "classifier_dist.txt").writeText(
            out.entries.joinToString("\n") + "\n--top unclassified--\n" + unk.entries.sortedByDescending { it.value }.take(40).joinToString("\n"))
    }
}
