package org.eclipse.orion.server.cf.node.commands;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.cf.commands.AbstractCFCommand;
import org.eclipse.orion.server.cf.objects.Target;

public abstract class AbstractNodeCFCommand extends AbstractCFCommand {

	protected IFileStore appStore;

	protected AbstractNodeCFCommand(Target target, IFileStore appStore) {
		super(target);
		this.appStore = appStore;
	}

}
