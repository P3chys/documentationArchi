package io.github.p3chys.documentationarchi.ui;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;

import com.archimatetool.editor.model.commands.AddListMemberCommand;
import com.archimatetool.editor.model.commands.EObjectFeatureCommand;
import com.archimatetool.editor.model.commands.RemoveListMemberCommand;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimatePackage;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IProperty;

/**
 * "Describe Here": the view an element is described on, kept in the element's {@value ModelReader#HOME} property
 * (the view's id), so it travels with the model like the print order. The change is one command that Undo takes back.
 */
final class HomeView {

    private HomeView() {
    }

    /**
     * Makes the view the home of the elements; when it is the home of all of them already, takes it away again.
     *
     * @return null when there are no elements
     */
    static Command toggle(IDiagramModel view, List<IArchimateElement> elements) {
        if (elements.isEmpty()) {
            return null;
        }
        boolean clear = elements.stream().allMatch(e -> view.getId().equals(ModelReader.home(e)));
        CompoundCommand command = new CompoundCommand(clear ? "Describe Elsewhere" : "Describe Here");
        for (IArchimateElement element : elements) {
            List<IProperty> homes = element.getProperties().stream().filter(p -> ModelReader.HOME.equals(p.getKey()))
                    .collect(Collectors.toList());
            if (clear) {
                homes.forEach(p -> command.add(new RemoveListMemberCommand<>(element.getProperties(), p)));
            } else if (homes.isEmpty()) {
                IProperty home = IArchimateFactory.eINSTANCE.createProperty();
                home.setKey(ModelReader.HOME);
                home.setValue(view.getId());
                command.add(new AddListMemberCommand<>(element.getProperties(), home));
            } else {
                if (!view.getId().equals(homes.get(0).getValue())) {
                    command.add(new EObjectFeatureCommand("Describe Here", homes.get(0),
                            IArchimatePackage.Literals.PROPERTY__VALUE, view.getId()));
                }
                // one home is enough
                for (IProperty extra : homes.subList(1, homes.size())) {
                    command.add(new RemoveListMemberCommand<>(element.getProperties(), extra));
                }
            }
        }
        return command.isEmpty() ? null : command;
    }
}
