/* $Id: ClusterMergerImpl.java,v 1.3 2007/12/04 10:26:43 bam Exp $
 * $Revision: 1.3 $
 * $Date: 2007/12/04 10:26:43 $
 * $Author: bam $
 *
 * The Summa project.
 * Copyright (C) 2005-2007  The State and University Library
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package dk.statsbiblioteket.summa.clusterextractor;

import dk.statsbiblioteket.summa.clusterextractor.data.Dendrogram;
import dk.statsbiblioteket.summa.clusterextractor.data.DendrogramNode;
import dk.statsbiblioteket.summa.clusterextractor.data.*;
import dk.statsbiblioteket.summa.clusterextractor.data.PQueueSimilarity;
import dk.statsbiblioteket.summa.clusterextractor.data.Vocabulary;
import dk.statsbiblioteket.summa.clusterextractor.math.CentroidVector;
import dk.statsbiblioteket.summa.clusterextractor.math.CoordinateComparator;
import dk.statsbiblioteket.summa.clusterextractor.math.IncrementalCentroid;
import dk.statsbiblioteket.summa.common.configuration.Configuration;
import dk.statsbiblioteket.util.qa.QAInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

/**
 * ClusterMergerImpl merges vocabularies and centroid sets.
 * The local builders push newly build vocabularies and centroid sets to the
 * merger. The merger merges the different local vocabularies into one
 * vocabulary and the different local centroid sets into one centroid set
 * and then into a dendrogram. The merged vocabulary and dendrogram is then
 * pushed to the known providers.
 */
@QAInfo(level = QAInfo.Level.NORMAL,
        state = QAInfo.State.IN_DEVELOPMENT,
        author = "bam",
        comment = "Methods needs Javadoc")
public class ClusterMergerImpl extends UnicastRemoteObject implements ClusterMerger {
    protected static final Log log = LogFactory.getLog(ClusterMergerImpl.class);
    /** Configurations. */
    protected Configuration conf;
    /** Merged Vocabulary. */
    private Vocabulary vocabulary;
    /** Merged Dendrogram. */
    private Dendrogram dendrogram;

    /**
     * Construct cluster merger using given configurations.
     * @param conf configurations
     * @throws java.rmi.RemoteException if failed to export object
     */
    public ClusterMergerImpl(Configuration conf) throws RemoteException {
        super();
        this.conf = conf;
        exportRemoteInterfaces();
    }

    public void uploadVocabulary(String machineId, long handle, Vocabulary vocab) {
        String directoryPath = conf.getString(IN_VOCAB_PATH_KEY);
        saveVocabulary(vocab, directoryPath);
    }

    public void mergeVocabularies() {
        this.vocabulary = loadAndMergeVocabularies();
        String directoryPath = conf.getString(OUT_VOCAB_PATH_KEY);
        saveVocabulary(vocabulary, directoryPath);
    }

    public Vocabulary getNewVocabulary() {
        return this.vocabulary;
    }

    public void uploadCentroidSet(String machineId, long handle, CentroidSet centroidSet) {
        String directoryPath = conf.getString(CENTROID_SETS_PATH_KEY);
        saveCentroids(centroidSet, directoryPath);
    }

    /**
     * Save given centroid set in given path.
     * @param centroids centroid set to save
     * @param centroidSetPath path to save in
     */
    private void saveCentroids(CentroidSet centroids, String centroidSetPath) {
        File localCentroidSetDirectory = new File(centroidSetPath);
        if (!localCentroidSetDirectory.exists()) {
            localCentroidSetDirectory.mkdir();
        }
        long timeStamp = System.currentTimeMillis();
        File file = new File(centroidSetPath+timeStamp+"centroids.set");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(centroids);
            oos.close();
        } catch (FileNotFoundException e) {
            log.error("FileNotFoundException in ClusterBuilderImpl.saveCentroids. " +
                    "The centroids cannot be saved.", e);
        } catch (IOException e) {
            log.error("IOException in ClusterBuilderImpl.saveCentroids. " +
                    "The centroids cannot be saved.", e);
        }
    }

    /**
     * Save given {@link Vocabulary} in given directory.
     * @param vocabulary Vocabulary to save
     * @param directoryPath path to directory to save in
     * @return the file the vocabulary was saved in
     */
    private File saveVocabulary(Vocabulary vocabulary, String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            directory.mkdir();
        }
        long timeStamp = System.currentTimeMillis();
        File file = new File(directoryPath+timeStamp+"vocabulary.obj");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(vocabulary);
            oos.close();
        } catch (FileNotFoundException e) {
            log.error("FileNotFoundException in ClusterMergerImpl.saveMergedVocabularies. " +
                    "The vocabulary cannot be saved.", e);
            return null;
        } catch (IOException e) {
            log.error("IOException in ClusterMergerImpl.saveMergedVocabularies. " +
                    "The vocabulary cannot be saved.", e);
            return null;
        }
        return file;
    }

    private Vocabulary loadAndMergeVocabularies() {
        String directoryPath = conf.getString(IN_VOCAB_PATH_KEY);
        File dir = new File(directoryPath);
        if (dir.isDirectory()) {
            File[] fileList = dir.listFiles();
            Vocabulary resultVocabulary = new Vocabulary();
            for (File file: fileList) {
                Vocabulary current = null;
                try {
                    FileInputStream fis = new FileInputStream(file);
                    ObjectInputStream ois = new ObjectInputStream(fis);
                    Object fileContent = ois.readObject();
                    if (fileContent instanceof Vocabulary) {
                        current = (Vocabulary) fileContent;
                    }
                    ois.close();
                } catch (FileNotFoundException e) {
                    log.warn("ClusterMergerImpl.loadVocabulary " +
                            "FileNotFoundException; file = " + file, e);
                } catch (IOException e) {
                    log.warn("ClusterMergerImpl.loadVocabulary " +
                            "IOException; file = " + file, e);
                } catch (ClassNotFoundException e) {
                    log.warn("ClusterMergerImpl.loadVocabulary " +
                            "ClassNotFoundException; file = " + file, e);
                }
                if (current != null) {
                    resultVocabulary.putAll(current);
                }
            }
            return resultVocabulary;
        } else {
            log.error("ClusterMergerImpl.loadVocabulary not able to load " +
                    "centroids; directoryPath = " + directoryPath +
                    " not a directory; null is returned.");
            return null;
        }
    }

    public void mergeCentroidSets() {
        CentroidSet centroids = loadCentroids();
        centroids = resolveNameConflicts(centroids);
        this.dendrogram = buildDendrogram(centroids);
        saveDendrogram(dendrogram);
    }

    public Dendrogram getNewDendrogram() {
        return this.dendrogram;
    }

    private void saveDendrogram(Dendrogram dendrogram) {
        String localDendrogramPath = conf.getString(DENDROGRAM_PATH_KEY);
        File localCentroidSetDirectory = new File(localDendrogramPath);
        if (!localCentroidSetDirectory.exists()) {
            localCentroidSetDirectory.mkdir();
        }
        long timeStamp = System.currentTimeMillis();
        String fileName = localDendrogramPath+timeStamp+"dendrogram.obj";
        try {
            FileOutputStream fos = new FileOutputStream(fileName);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(dendrogram);
            oos.close();
        } catch (FileNotFoundException e) {
            log.error("FileNotFoundException in ClusterMergerImpl.saveDendrogram. " +
                    "The dendrogram cannot be saved.", e);
        } catch (IOException e) {
            log.error("IOException in ClusterMergerImpl.saveDendrogram. " +
                    "The dendrogram cannot be saved.", e);
        }
    }

    private Dendrogram buildDendrogram(Set<CentroidVector> centroids) {
        if (centroids==null || centroids.isEmpty()) {
            log.warn("ClusterMergerImpl buildDendrogram centroid set empty.");
            return null;
        }

        //get join similarity threshold
        double joinSimThreshold =
                getDoubleProperty(JOIN_SIMILARITY_THRESHOLD_KEY, .9);

        //initialise work array; note that as we are building a binary tree
        //bottom up, the total number of nodes is twice the starting number
        DendrogramNode[] workArray = new DendrogramNode[centroids.size()*2+1];
        int size = centroids.size();
        int index = 0;
        for (CentroidVector centroid: centroids) {
            workArray[index] = new DendrogramNode(centroid);
            index++;
        }

        //TODO: make PQueueSimilarity an inner class?
        //as it only makes sense relative to the work array

        //initialise pQueue
        //the initial size of the pQueue is size^2 and the maximum size is the double
        //TODO: worry about the space used by p-queue
        PriorityQueue<PQueueSimilarity> pQueue = new PriorityQueue<PQueueSimilarity>(2*size*size);
        for (int i=0; i<size-1; i++) {
            for (int j=i+1; j<size; j++) {
                double simValue = workArray[i].getCentroid().similarity(workArray[j].getCentroid());
                pQueue.offer(new PQueueSimilarity(simValue, i, j));
            }
        }
        //build dendrogram (look at notes)
        DendrogramNode parent = null;
        while (!pQueue.isEmpty()) {
            PQueueSimilarity sim = pQueue.poll();
            //'old similarities' are not removed when outdated
            //but simply skipped when encountered
            if (workArray[sim.getI()]==null || workArray[sim.getJ()]==null) {
                continue;
            }
            DendrogramNode nodeI = workArray[sim.getI()];
            DendrogramNode nodeJ = workArray[sim.getJ()];
            //create parent (centroid name not important)
            String name = combineName(nodeI.getCentroid(), nodeJ.getCentroid());
            CentroidVector newCentroidVec =
                    join(nodeI.getCentroid(), nodeJ.getCentroid(), name);
            parent = new DendrogramNode(newCentroidVec);
            //if node i and j centroids are 'too' similar, throw away originals,
            if (sim.getValue()>joinSimThreshold) {
                if (log.isTraceEnabled()) {
                    log.trace("Combining node i = '"+nodeI.getCentroid().getClusterName()+
                            "' and node j = '"+nodeJ.getCentroid().getClusterName()+"'; similarity = "+
                            sim.getValue() + "; new name = " + name);
                }
            } else { //else add node i and j as children
                parent.addChild(nodeI);
                parent.addChild(nodeJ);
            }
            //delete 'old' nodes from work list
            workArray[sim.getI()] = null;
            workArray[sim.getJ()] = null;
            //add parent to worklist and add new similarities to p-queue
            workArray[size] = parent;
            for (int k=0; k<size; k++) {
                if (workArray[k]!=null) {
                    double simValue = workArray[k].getCentroid().similarity(parent.getCentroid());
                    pQueue.offer(new PQueueSimilarity(simValue, k, size));
                }
            }
            size++;
        }

        return new Dendrogram(parent);
        //TODO: test buildDendrogram
        //special case: centroid set empty
    }

    /**
     * Combine the names of two clusters in a 'reasonable' way.
     * @param first centroid for the first cluster
     * @param second centroid for the second cluster
     * @return a new name for the combined cluster
     */
    private String combineName(CentroidVector first, CentroidVector second) {
        //TODO: check combineName
        String name;
        if (first.getClusterName().toLowerCase().equals(
                second.getClusterName().toLowerCase())) {
            name = first.getClusterName();
        } else {
            String semicolon = ";";
            name = first.getClusterName();
            String[] names = first.getClusterName().split(semicolon);
            String[] namesSecond = second.getClusterName().split(semicolon);
            for (String nameFromSecondCentroid : namesSecond) {
                boolean nameFromSecondOk = true;
                for (String nameFromFirst : names) {
                    if (nameFromSecondCentroid.equals(nameFromFirst)) {
                        nameFromSecondOk = false;
                    }
                }
                if (nameFromSecondOk) {
                    name = name + semicolon + nameFromSecondCentroid;
                }
            }
            //should the combination be 'largest first'?
            //should the following two ifs be dropped?
            if (second.getExpectedSize()<.2*first.getExpectedSize()) {
                name = first.getClusterName();
            }
            if (first.getExpectedSize()<.2*second.getExpectedSize()) {
                name = second.getClusterName();
            }
        }
        return name;
    }

    private CentroidSet resolveNameConflicts(CentroidSet centroids) {
        //todo update resolve name conflicts
        if (centroids==null) {return null;}

        //get join same name similarity threshold
        double joinSimThresholdSameName =
                getDoubleProperty(JOIN_SIMILARITY_THRESHOLD_SAME_NAME_KEY, .8);

        //centroid array used for the following loops
        CentroidVector[] centroidArray =
                centroids.toArray(new CentroidVector[centroids.size()]);

        //set for new joined centroids
        CentroidSet newCentroidsSet = new CentroidSet();

        int listSize = centroidArray.length;
        for (int i=0; i<listSize; i++) {
            CentroidVector first = centroidArray[i];

            for (int j=i+1; j<listSize; j++) {
                if (first == null) {break;}

                CentroidVector second = centroidArray[j];
                if (second == null) {continue;}

                // join the two centroids 'first' and 'second', if the names of the clusters
                // are the same and the two clusters are close, otherwise rename both
                if (first.getClusterName().toLowerCase().equals(
                        second.getClusterName().toLowerCase())) {

                    if (first.similarity(second)>joinSimThresholdSameName) {
                        //find a new name for the cluster
                        String name = first.getClusterName().toLowerCase();
                        if (log.isTraceEnabled()) {
                            log.trace("Combining first = '"+first.getClusterName()+
                                    "' and second = '"+second.getClusterName()+"'; similarity = "+
                                    first.similarity(second) + "; new name = " + name);
                        }

                        //create new centroid based on the two known centroids
                        CentroidVector newCentroidVec = join(first, second, name);

                        //add new centroid to the new centroid set
                        newCentroidsSet.add(newCentroidVec);
                        //update first
                        first = newCentroidVec;

                        //remove 'old' centroids from work array
                        centroidArray[i] = null;
                        centroidArray[j] = null;
                    } else {
                        //rename using 'top' coordinates
                        //TODO: think about rename

                        //get coordinates for first; sort descending
                        List<Map.Entry<String, Number>> coorFirst =
                                new ArrayList<Map.Entry<String, Number>>(first.getCoordinates().entrySet());
                        Collections.sort(coorFirst, Collections.reverseOrder(new CoordinateComparator()));
                        //get coordinates for second; sort descending
                        List<Map.Entry<String, Number>> coorSecond =
                                new ArrayList<Map.Entry<String, Number>>(second.getCoordinates().entrySet());
                        Collections.sort(coorSecond, Collections.reverseOrder(new CoordinateComparator()));
                        //add coordinate names to centroid names
                        int index = 0;
                        while (first.getClusterName().toLowerCase().equals(
                                second.getClusterName().toLowerCase())) {
                            if (!first.getClusterName().toLowerCase().equals(coorFirst.get(index).getKey())) {
                                first.setName(first.getClusterName().toLowerCase() + "; " + coorFirst.get(index).getKey());
                            }
                            if (!second.getClusterName().toLowerCase().equals(coorSecond.get(index).getKey())) {
                                second.setName(second.getClusterName().toLowerCase() + "; " + coorSecond.get(index).getKey());
                            }
                            index++;
                        }
                    }
                }
            }
        }
        //copy all the old centroids that survived to the new centroid set
        for (CentroidVector vec: centroidArray) {
            if (vec != null) {
                newCentroidsSet.add(vec);
            }
        }

        return newCentroidsSet;
    }

    public CentroidVector join(CentroidVector first, CentroidVector second, String name) {
        IncrementalCentroid newIncCentroid = new IncrementalCentroid(name);
        //TODO: should the centroids be weighted with expected size?
        newIncCentroid.addPoint(first);
        newIncCentroid.addPoint(second);
        CentroidVector newCentroidVec = newIncCentroid.getCentroidVector();

        newCentroidVec.setExpectedSize(first.getExpectedSize() + second.getExpectedSize());
        //TODO: new similarity threshold is important - look at notes
        if (log.isTraceEnabled()) {
            log.trace("first.getSimilarityThreshold() = " + first.getSimilarityThreshold());
            log.trace("newCentroidVec.similarity(first) = " + newCentroidVec.similarity(first));
            log.trace("second.getSimilarityThreshold() = " + second.getSimilarityThreshold());
            log.trace("newCentroidVec.similarity(second) = " + newCentroidVec.similarity(second));
        }

        double simThrsAngle1 = Math.acos(first.getSimilarityThreshold());
        double simNewAngle1 = Math.acos(newCentroidVec.similarity(first));
        double simThrsAngle2 = Math.acos(second.getSimilarityThreshold());
        double simNewAngle2 = Math.acos(newCentroidVec.similarity(second));
        double maxAngle = Math.max(simThrsAngle1 + simNewAngle1,
                simThrsAngle2 + simNewAngle2);
        if (maxAngle>Math.PI/2) {
            newCentroidVec.setSimilarityThreshold(0);
            log.trace("Similarity threshold set to 0.");
            //TODO: once we reach similarity threshold 0, there is no point in
            //TODO: continuing building the dendrogram - we might as well have a forest...
        } else {
            newCentroidVec.setSimilarityThreshold(Math.cos(maxAngle));
            log.trace("Similarity threshold set to " + Math.cos(maxAngle) + ".");
        }

        return newCentroidVec;
    }

    private double getDoubleProperty(String key, double defaultValue) {
        double value;
        try {
            value = Double.parseDouble(conf.getString(key));
        } catch (NumberFormatException e) {
            log.warn("ClusterMergerImpl.getDoubleProperty: The " +
                    "double property with key " + key +
                    "could not be parsed.", e);
            value = defaultValue;
        }
        return value;
    }

    /**
     * Load and join centroid sets from all the local centroid builders.
     * @return set of all centroids from all local builders
     */
    private CentroidSet loadCentroids() {
        String centroidSetsPath = conf.getString(CENTROID_SETS_PATH_KEY);
        File dir = new File(centroidSetsPath);
        if (dir.isDirectory()) {
            File[] centroidSetsFileList = dir.listFiles();
            CentroidSet resultCentroidSet = new CentroidSet();
            for (File file: centroidSetsFileList) {
                Set<CentroidVector> centroidSet = null;
                try {
                    FileInputStream fis = new FileInputStream(file);
                    ObjectInputStream ois = new ObjectInputStream(fis);
                    Object fileContent = ois.readObject();
                    if (fileContent instanceof CentroidSet) {
                        centroidSet = (CentroidSet) fileContent;
                    }
                    ois.close();
                } catch (FileNotFoundException e) {
                    log.warn("ClusterMergerImpl.loadCentroids " +
                            "FileNotFoundException; file = " + file, e);
                } catch (IOException e) {
                    log.warn("ClusterMergerImpl.loadCentroids " +
                            "IOException; file = " + file, e);
                } catch (ClassNotFoundException e) {
                    log.warn("ClusterMergerImpl.loadCentroids " +
                            "ClassNotFoundException; file = " + file, e);
                }
                if (centroidSet != null) {
                    resultCentroidSet.addAll(centroidSet);
                }
            }
            return resultCentroidSet;
        } else {
            log.error("ClusterMergerImpl.loadCentroids not able to load " +
                    "centroids; centroidSetsPath = " + centroidSetsPath +
                    " not a directory; null is returned.");
            return null;
        }
    }

    /**
     * Expose the Merger as a remote service over rmi.
     * See dk.statsbiblioteket.summa.score.client.Client for implementation.
     * @throws RemoteException if failed to export remote interfaces
     */
    private void exportRemoteInterfaces() throws RemoteException {
        //TODO implement exportRemoteInterfaces
    }
}
