/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.umcg.westrah.binarymetaanalyzer;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import java.io.IOException;
import java.util.ArrayList;
import umcg.genetica.io.Gpio;
import umcg.genetica.io.text.TextFile;

/**
 *
 * @author Harm-Jan
 */
public class BinaryMetaAnalysisDataset {

    boolean isCisDataset = false;
    private final String datasetLoc;
    private MetaQTL4MetaTrait[][] snpCisProbeMap;

    private long[] snpBytes;
    private String[] alleles;
    private String[] allelesAssessed;
    private String[] minorAlleles;
    private int[] n;
    private double[] callrates;
    private double[] hwes;
    private double[] mafs;
    private String[] probeList;
    private int nrProbes;
    private String[] snps;
    private final MetaQTL4TraitAnnotation probeAnnotation;
    private final int platformId;

    public BinaryMetaAnalysisDataset(String dir, boolean isCisDataset, int permutation, String platform, MetaQTL4TraitAnnotation probeAnnotation) throws IOException {
        String matrix = dir;
        String probeFile = dir;
        String snpFile = dir;
        this.probeAnnotation = probeAnnotation;
        this.platformId = probeAnnotation.getPlatformId(platform);
        if (permutation > 0) {
            matrix += "Dataset-PermutationRound-" + permutation + ".dat";
            probeFile += "Dataset-PermutationRound-" + permutation + "-ColNames.txt.gz";
            snpFile += "Dataset-PermutationRound-" + permutation + "-RowNames.txt.gz";
        } else {
            matrix += "Dataset.dat";
            probeFile += "Dataset-ColNames.txt.gz";
            snpFile += "Dataset-RowNames.txt.gz";
        }
        this.isCisDataset = isCisDataset;
        this.datasetLoc = dir;
        // check presence of files
        if (!Gpio.exists(matrix)) {
            throw new IOException("Could not find file: " + matrix);
        }
        if (!Gpio.exists(probeFile)) {
            throw new IOException("Could not find file: " + probeFile);
        }
        if (!Gpio.exists(snpFile)) {
            throw new IOException("Could not find file: " + snpFile);
        }

        loadSNPs(snpFile);
        loadProbes(probeFile);
    }

    private void loadSNPs(String snpFile) throws IOException {
        // get nr of lines
        TextFile tf = new TextFile(snpFile, TextFile.R);
        int nrSNPs = tf.countLines();
        tf.close();

        snpBytes = new long[nrSNPs];
        alleles = new String[nrSNPs];
        allelesAssessed = new String[nrSNPs];
        minorAlleles = new String[nrSNPs];
        n = new int[nrSNPs];
        callrates = new double[nrSNPs];
        hwes = new double[nrSNPs];
        mafs = new double[nrSNPs];

        if (isCisDataset) {
            // jagged array, hurrah
            snpCisProbeMap = new MetaQTL4MetaTrait[nrSNPs][0];
        }

        tf.open();
        String[] elems = tf.readLineElems(TextFile.tab);
        int ln = 0;

        snps = new String[nrSNPs];
        while (elems != null) {
            String snp = new String(elems[0].getBytes("UTF-8")).intern();
            String allelesStr = new String(elems[1].getBytes("UTF-8")).intern();
            String minorAlleleStr = new String(elems[2].getBytes("UTF-8")).intern();
            String alleleAssessedStr = new String(elems[3].getBytes("UTF-8")).intern();

            snps[ln] = snp;
            alleles[ln] = allelesStr;
            allelesAssessed[ln] = alleleAssessedStr;
            minorAlleles[ln] = minorAlleleStr;

            int nrCalled = 0;
            double maf = 0;
            double cr = 0;
            double hwe = 0;
            int nrZScores = 0;

            try {
                nrCalled = Integer.parseInt(elems[4]);
            } catch (NumberFormatException e) {
                System.err.println("ERROR: nrCalled is not an int (input: " + elems[4] + ") for dataset: " + datasetLoc + " on line: " + ln);
            }
            try {
                maf = Double.parseDouble(elems[5]);
            } catch (NumberFormatException e) {
                System.err.println("ERROR: maf is not a double (" + elems[5] + ") for dataset: " + datasetLoc + " on line: " + ln);
            }
            try {
                cr = Double.parseDouble(elems[6]);
            } catch (NumberFormatException e) {
                System.err.println("ERROR: cr is not a double (" + elems[6] + ") for dataset: " + datasetLoc + " on line: " + ln);
            }
            try {
                hwe = Double.parseDouble(elems[7]);
            } catch (NumberFormatException e) {
                System.err.println("ERROR: hwe is not a double (" + elems[7] + ") for dataset: " + datasetLoc + " on line: " + ln);
            }
            try {
                nrZScores = Integer.parseInt(elems[8]);
            } catch (NumberFormatException e) {
                System.err.println("ERROR: nrZScores is not an int (input: " + elems[8] + ") for dataset: " + datasetLoc + " on line: " + ln);
            }

            n[ln] = nrCalled;
            callrates[ln] = cr;
            hwes[ln] = hwe;
            mafs[ln] = maf;

            if (ln + 1 < nrSNPs) {
                snpBytes[ln + 1] = snpBytes[ln] + (nrZScores * 2);
            }

            if (isCisDataset) {
                MetaQTL4MetaTrait[] snpProbeList = new MetaQTL4MetaTrait[(elems.length - 8)];
                for (int e = 8; e < elems.length; e++) {
                    // get the list of probes for this particular SNP.
                    String probe = elems[e];
                    MetaQTL4MetaTrait t = probeAnnotation.getTraitForPlatformId(platformId, probe);
                    snpProbeList[e-8] = t;
                }
                snpCisProbeMap[ln] = snpProbeList;
            }
            elems = tf.readLineElems(TextFile.tab);
            ln++;
        }
        tf.close();

    }

    private void loadProbes(String columns) throws IOException {
        TextFile tf = new TextFile(columns, TextFile.R);
        nrProbes = tf.countLines();
        tf.close();

        ArrayList<String> allProbes = tf.readAsArrayList();
        probeList = allProbes.toArray(new String[0]);
    }

    public MetaQTL4MetaTrait[] getCisProbes(int snp) {
        return snpCisProbeMap[snp];
    }

    public float[] getZScores(int snp) throws IOException {
        long snpBytePos = snpBytes[snp];
        return null;
    }

    public String[] getSNPs() {
        return snps;
    }

    public String[] getProbeList() {
        return probeList;
    }

    public int getSampleSize(int datasetSNPId) {
        return n[datasetSNPId];
    }

    String getAlleles(int datasetSNPId) {
        return alleles[datasetSNPId];
    }

    String getAlleleAssessed(int datasetSNPId) {
        return allelesAssessed[datasetSNPId];
    }

}
