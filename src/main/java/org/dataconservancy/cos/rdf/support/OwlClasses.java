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

/**
 * Supported OWL classes.  Java classes annotated with {@link org.dataconservancy.cos.rdf.annotations.OwlIndividual} may
 * be mapped to the OWL classes enumerated here.
 */
public enum OwlClasses {

    OSF_BO (Rdf.Ns.OSF, "OsfObject"),

    OSF_USER (Rdf.Ns.OSF, "User"),

    OSF_NODEBASE (Rdf.Ns.OSF, "NodeBase"),

    OSF_NODE (Rdf.Ns.OSF, "Node"),

    OSF_REGISTRATION (Rdf.Ns.OSF, "Registration"),

    OSF_COLLECTION (Rdf.Ns.OSF, "Collection"),

    OSF_DATAENTITY (Rdf.Ns.OSF, "DataEntity"),

    OSF_CONTRIBUTOR (Rdf.Ns.OSF, "Contributor"),

    OSF_FILEBASE (Rdf.Ns.OSF, "FileBase"),

    OSF_FILE (Rdf.Ns.OSF, "File"),

    OSF_FOLDER (Rdf.Ns.OSF, "Folder"),

    OSF_FILEENTITY (Rdf.Ns.OSF, "FileEntity");
    
    private String ns;

    private String localname;

    private OwlClasses(String ns, String localname) {
        this.ns = ns;
        this.localname = localname;
    }

    public String ns() {
        return ns;
    }

    public String localname() {
        return localname;
    }

    public String fqname() {
        return ns + localname;
    }

    public String value() {
        return fqname();
    }

}
