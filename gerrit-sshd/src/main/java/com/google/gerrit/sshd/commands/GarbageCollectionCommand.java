// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

/** Runs the Git garbage collection. */
@RequiresCapability(GlobalCapability.RUN_GC)
@CommandMetaData(name = "gc", description = "Run Git garbage collection",
  runsAt = MASTER_OR_SLAVE)
public class GarbageCollectionCommand extends SshCommand {

  @Option(name = "--all", usage = "runs the Git garbage collection for all projects")
  private boolean all;

  @Option(name = "--show-progress", usage = "progress information is shown")
  private boolean showProgress;

  @Argument(index = 0, required = false, multiValued = true, metaVar = "NAME",
      usage = "projects for which the Git garbage collection should be run")
  private List<ProjectControl> projects = new ArrayList<>();

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GarbageCollection.Factory garbageCollectionFactory;

  @Override
  public void run() throws Exception {
    verifyCommandLine();
    runGC();
  }

  private void verifyCommandLine() throws UnloggedFailure {
    if (!all && projects.isEmpty()) {
      throw new UnloggedFailure(1,
          "needs projects as command arguments or --all option");
    }
    if (all && !projects.isEmpty()) {
      throw new UnloggedFailure(1,
          "either specify projects as command arguments or use --all option");
    }
  }

  private void runGC() {
    List<Project.NameKey> projectNames;
    if (all) {
      projectNames = Lists.newArrayList(projectCache.all());
    } else {
      projectNames = Lists.newArrayListWithCapacity(projects.size());
      for (ProjectControl pc : projects) {
        projectNames.add(pc.getProject().getNameKey());
      }
    }

    GarbageCollectionResult result =
        garbageCollectionFactory.create().run(projectNames, showProgress ? stdout : null);
    if (result.hasErrors()) {
      for (GarbageCollectionResult.Error e : result.getErrors()) {
        String msg;
        switch (e.getType()) {
          case REPOSITORY_NOT_FOUND:
            msg = "error: project \"" + e.getProjectName() + "\" not found";
            break;
          case GC_ALREADY_SCHEDULED:
            msg = "error: garbage collection for project \""
                + e.getProjectName() + "\" was already scheduled";
            break;
          case GC_FAILED:
            msg = "error: garbage collection for project \"" + e.getProjectName()
                + "\" failed";
            break;
          default:
            msg = "error: garbage collection for project \"" + e.getProjectName()
                + "\" failed: " + e.getType();
        }
        stdout.print(msg + "\n");
      }
    }
  }
}
