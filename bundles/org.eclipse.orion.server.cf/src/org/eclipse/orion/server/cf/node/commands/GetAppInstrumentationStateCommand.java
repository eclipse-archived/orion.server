package org.eclipse.orion.server.cf.node.commands;

import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.orion.server.cf.objects.Target;
import org.eclipse.orion.server.core.ServerStatus;

public class GetAppInstrumentationStateCommand extends AbstractNodeCFCommand {

	public GetAppInstrumentationStateCommand(Target target, IFileStore appStore) {
		super(target, appStore);
	}

	@Override
	protected ServerStatus _doIt() {

		return null;
	}

}
