/*
 * Copyright 2016 Johns Hopkins University
 *
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
package org.dataconservancy.cos.rdf.support;

import org.apache.jena.ontology.Individual;
import org.dataconservancy.cos.rdf.annotations.AnonIndividual;
import org.dataconservancy.cos.rdf.annotations.OwlIndividual;
import org.dataconservancy.cos.rdf.annotations.OwlProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Processes Java objects that may have OWL annotations present, and generates OWL individuals and properties from those
 * Java objects.
 * <p>
 * The OWL annotations may not use arbitrary URIs to identify OWL classes and properties; an ontology
 * must be supplied to the {@link OntologyManager}.  Only those OWL classes and properties that appear in the ontology
 * may be used in the Java OWL annotations.  In other words, the {@code OntologyManager} must have knowledge of the
 * OWL properties and classes it is being asked to map.
 * </p>
 * <p>
 * This class is able to reason over the following annotations and their attributes:
 * </p>
 * <dl>
 *     <dt>{@link OwlIndividual}</dt>
 *     <dd>Class-level annotation defining a Java class as an OWL individual</dd>
 *     <dt>{@link org.dataconservancy.cos.rdf.annotations.IndividualUri}</dt>
 *     <dd>Field-level annotation defining the value of the field as the identifier for an OWL individual</dd>
 *     <dt>{@link OwlProperty}</dt>
 *     <dd>Field-level annotation defining the value of the field as the object of the OWL property</dd>
 *     <dt>{@link AnonIndividual}</dt>
 *     <dd>Field-level annotation defining the value of the field as an anonymous OWL individual</dd>
 * </dl>
 * <p>
 * The intent of this class is to process Java objects that posses OWL annotations according to some ontology.
 * Annotated Java objects will be mapped from their Java representation to RDF.  Each invocation of the
 * {@code process(...)} method will result in OWL individuals and properties being added to the underlying
 * {@link ManagedGraph}.  The {@code process(...)} method can be invoked any number of times.  However, if you wish to
 * keep generated triples in separate graphs, you'll need to re-create a new instance of the
 * {@code AnnotationsProcessor} and its collaborating objects, documented below.
 * </p>
 * <p>
 * It may be a little obtuse, and subject to change in the future, but this is how the facades relate to each other and
 * to other classes in this package
 * </p>
 * <pre>
 * Jena OntModel &lt;-- OntologyManager &lt;-- Managed Graph &lt;-- AnnotationsProcessor
 *                                                                   /
 *                                       OwlAnnotationsProcessor &lt;--+
 * </pre>
 * <h3>Shortcomings</h3>
 * <ul>
 *   <li>I'm sure there are many</li>
 * </ul>
 *
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class AnnotationsProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationsProcessor.class);

    /**
     * Graph that stores the triples generated by this processor
     */
    private ManagedGraph graph;

    /**
     * Creates an {@code AnnotationsProcessor} that uses the underlying {@link ManagedGraph} to store generated
     * triples.
     *
     * @param graph the graph that stores generated triples
     */
    public AnnotationsProcessor(final ManagedGraph graph) {
        this.graph = graph;
    }

    /**
     * Accepts an object and processes class-level and field-level OWL annotations.  Recurses up the class hierarchy
     * to process class-level and field-level OWL annotations of super classes.  Fields that are {@code Collection} or
     * {@code Array} types will be reflected upon, and their types will also be processed and recursed.  Classes that
     * belong to the {@code java.*}, {@code javax.*}, and {@code sun.*} will not be processed.
     * <p>
     * This method will produce OWL individuals and properties in the underlying {@code PackageGraph}.  A map
     * of individual ids to individuals will be returned.  Anonymous individuals will have an implementation-specific
     * form of id, identified individuals will have URIs as identifiers.
     * </p>
     *
     * @param toProcess the object to map to OWL RDF
     * @return a map of individual identifiers to individuals that were created by this invocation.
     */
    public Map<String, Individual> process(final Object toProcess) {

        final Set<String> seen = new HashSet<>();

        final Map<String, Individual> createdIndividuals = new HashMap<>();
        final AnnotatedElementPairMap<AnnotatedElementPair, AnnotationAttributes> annotations =
                new AnnotatedElementPairMap<>();
        OwlAnnotationProcessor.getAnnotationsForInstance(toProcess, annotations);

        final OwlClasses owlClass = OwlAnnotationProcessor.getOwlClass(toProcess, annotations);
        final String id = OwlAnnotationProcessor.getIndividualId(null, toProcess, annotations);

        final Individual individual = graph.newIndividual(owlClass, id);
        createdIndividuals.put(individual.getURI(), individual);
        LOG.trace("Created individual with id {} for class {}", individual.getURI(), owlClass.fqname());
        seen.add(toProcess.getClass().getName());
        process(toProcess, individual, annotations, createdIndividuals, seen);
        return createdIndividuals;

    }

    /**
     * Provides a recursive entry point to process objects.
     *
     * @param toProcess the object to map to OWL RDF
     * @param enclosingIndividual the OWL individual that will be the subject of any triples added in this processing
     *                            step
     * @param annotations all the annotations that appear in the Java object graph
     * @param createdIndividuals maintains a map of identifiers to OWL individuals that have been created thus far
     */
    void process(final Object toProcess, final Individual enclosingIndividual,
                 final Map<AnnotatedElementPair, AnnotationAttributes> annotations,
                 final Map<String, Individual> createdIndividuals, final Set<String> seen) {
        if (seen.contains(toProcess.getClass().getName())) {
            LOG.trace("Skipping seen class: {}", toProcess.getClass().getName());
        }
        ReflectionUtils.doWithFields(toProcess.getClass(),
                (field) -> process(enclosingIndividual, toProcess, field, annotations, createdIndividuals, seen),
                (field) -> annotations.containsKey(AnnotatedElementPair.forPair(field, OwlProperty.class)));
    }

    private void process(final Individual enclosingIndividual, final Object enclosingObject,
                         final Field field, final Map<AnnotatedElementPair, AnnotationAttributes> annotations,
                         final Map<String, Individual> createdIndividuals, final Set<String> seen) {
        LOG.trace("  Processing field '{}' (a {}) for OWL {} {}",
                field.getName(), field.getType(),
                (enclosingIndividual.isAnon() ? "anonymous individual" : "individual"),
                (enclosingIndividual.isAnon() ? enclosingIndividual.getId() : enclosingIndividual.getURI()));
        // if the field is not annotated with @OwlProperty, then we have nothing to do.
        // the caller should be responsible for this.
        // The OwlProperty that will be used to add the field to the enclosing object
        final OwlProperties owlProperty = annotations
                .get(AnnotatedElementPair.forPair(field, OwlProperty.class)).getEnum("value");

        final Object fieldValue;

        try {
            field.setAccessible(true);
            if ((fieldValue = field.get(enclosingObject)) == null) {
                LOG.trace("  Skipping processing of null value on field {} for OWL property {}",
                        field.getName(), owlProperty.localname());
                return;
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    String.format("Unable to access field %s (type %s, on class %s) for OWL property %s: %s",
                            field.getName(), field.getType(), enclosingObject.getClass().getName(),
                                owlProperty.fqname(), e.getMessage()), e);
        }

        // The annotated element pairs that apply to the supplied field
        final Set<AnnotatedElementPair> fieldAnnotationPairs = annotations.keySet().stream()
                .filter((aep) -> aep.getAnnotatedElement().equals(field))
                .collect(Collectors.toSet());


        // The objects of the OWL property.  If the field is a collection or an array type, there may be multiple
        // objects.
        final Stream<?> objectsToProcess = OwlAnnotationProcessor.unwrap(field, fieldValue);

        objectsToProcess.forEach(objectToProcess -> {
            final Object value;

            if ((value = OwlAnnotationProcessor.transform(
                    enclosingObject, field, objectToProcess, annotations)) == null) {
                LOG.trace("  Result of transformation was null.  " +
                        "Skipping processing of transformed null value on field {} for OWL property {}",
                        field.getName(), owlProperty.localname());
                return;
            }

            if (!owlProperty.object()) {
                LOG.trace("  Adding literal {} with value '{}' to {} {}",
                        owlProperty.localname(), value,
                        (enclosingIndividual.isAnon() ? "anonymous individual" : "individual"),
                        (enclosingIndividual.isAnon() ? enclosingIndividual.getId() : enclosingIndividual.getURI()));
                graph.addLiteral(enclosingIndividual, owlProperty.fqname(), value);
            } else {
                // if anon individual, create the individual and recurse, processing the properties of the
                // AnonIndividual class
                if (fieldAnnotationPairs.contains(AnnotatedElementPair.forPair(field, AnonIndividual.class))) {
                    final OwlClasses targetOwlClass = annotations.get(
                            AnnotatedElementPair.forPair(field, AnonIndividual.class)).getEnum("value");
                    final Individual anonIndividual = graph.newIndividual(targetOwlClass);
                    graph.addAnonIndividual(enclosingIndividual, owlProperty.fqname(), anonIndividual);
                    createdIndividuals.put(anonIndividual.getId().toString(), anonIndividual);
                    LOG.trace("  Created anonymous individual with id {} for class {}",
                            anonIndividual.getId(), targetOwlClass.fqname());
                    final AnnotatedElementPairMap<AnnotatedElementPair, AnnotationAttributes> individualAnnotations =
                            new AnnotatedElementPairMap<>();
                    OwlAnnotationProcessor.getAnnotationsForInstance(objectToProcess, individualAnnotations);
                    seen.add(objectToProcess.getClass().getName());
                    process(objectToProcess, anonIndividual, individualAnnotations, createdIndividuals, seen);
                } else if (annotations.containsKey(
                        AnnotatedElementPair.forPair(objectToProcess.getClass(), OwlIndividual.class))) {
                    // if identified individual, create the individual and recurse, processing the properties of the
                    // OwlIndividual class
                    final String id = OwlAnnotationProcessor.getIndividualId(
                            enclosingObject, objectToProcess, annotations);
                    final OwlClasses targetOwlClass = annotations.get(
                            AnnotatedElementPair.forPair(
                                    objectToProcess.getClass(), OwlIndividual.class)).getEnum("value");
                    final Individual idIndividual = graph.newIndividual(targetOwlClass, id);
                    graph.addIndividual(enclosingIndividual, owlProperty.fqname(), idIndividual.getURI());
                    createdIndividuals.put(idIndividual.getURI(), idIndividual);
                    LOG.trace("  Created individual with id {} for class {}",
                            idIndividual.getURI(), targetOwlClass.fqname());
                    final AnnotatedElementPairMap<AnnotatedElementPair, AnnotationAttributes> individualAnnotations =
                            new AnnotatedElementPairMap<>();
                    OwlAnnotationProcessor.getAnnotationsForInstance(objectToProcess, individualAnnotations);
                    seen.add(objectToProcess.getClass().getName());
                    process(objectToProcess, idIndividual, individualAnnotations, createdIndividuals, seen);
                } else {
                    // resource
                    LOG.trace("  Adding resource {} with value {} to {} {}",
                            owlProperty.localname(), Util.asResource(value.toString()),
                            (enclosingIndividual.isAnon() ? "anonymous individual" : "individual"),
                            (enclosingIndividual.isAnon() ?
                                    enclosingIndividual.getId() : enclosingIndividual.getURI()));
                    graph.addResource(enclosingIndividual, owlProperty.fqname(), Util.asResource(value.toString()));
                }
            }
        });
    }

}
