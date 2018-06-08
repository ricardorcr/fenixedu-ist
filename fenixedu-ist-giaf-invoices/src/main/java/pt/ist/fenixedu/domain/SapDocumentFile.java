package pt.ist.fenixedu.domain;

import org.fenixedu.bennu.core.domain.User;

public class SapDocumentFile extends SapDocumentFile_Base {
    
    public SapDocumentFile(String filename, byte[] content) {
        super();
        init(filename, filename, content);
    }
    
    @Override
    public boolean isAccessible(User user) {
        return true;
    }
}
