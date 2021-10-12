// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.usage.storage;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.sosy_lab.cpachecker.cpa.usage.CompatibleNode;
import org.sosy_lab.cpachecker.cpa.usage.UsageInfo.Access;

public final class UsagePoint implements Comparable<UsagePoint> {

  private final Access access;
  private final List<CompatibleNode> compatibleNodes;
  private final Set<UsagePoint> coveredUsages;

  public UsagePoint(List<CompatibleNode> nodes, Access pAccess) {
    access = pAccess;
    coveredUsages = new TreeSet<>();
    compatibleNodes = nodes;
  }

  public boolean addCoveredUsage(UsagePoint newChild) {
    synchronized (this) {
      if (!coveredUsages.contains(newChild)) {

        for (UsagePoint point : coveredUsages) {
          if (point.covers(newChild)) {
            assert !point.equals(newChild);
            return point.addCoveredUsage(newChild);
          }
        }
        return coveredUsages.add(newChild);
      }
      return false;
    }
  }

  public Set<UsagePoint> getCoveredUsages() {
    return coveredUsages;
  }

  @Override
  public int hashCode() {
    return Objects.hash(access, compatibleNodes);
  }

  public Access getAccess() {
    return access;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null ||
        getClass() != obj.getClass()) {
      return false;
    }
    UsagePoint other = (UsagePoint) obj;
    return access == other.access
        && Objects.equals(compatibleNodes, other.compatibleNodes);
  }

  @Override
  public int compareTo(UsagePoint o) {
    //It is very important to compare at first the accesses, because an algorithm base on this suggestion
    int result = access.compareTo(o.access);
    if (result != 0) {
      return result;
    }
    Preconditions.checkArgument(compatibleNodes.size() == o.compatibleNodes.size());
    for (int i = 0; i < compatibleNodes.size(); i++) {
      CompatibleNode currentNode = compatibleNodes.get(i);
      CompatibleNode otherNode = o.compatibleNodes.get(i);
      result = currentNode.compareTo(otherNode);
      if (result != 0) {
        return result;
      }
    }
    return result;
  }

  //TODO CompareTo? with enums
  public boolean covers(UsagePoint o) {
    // access 'write' is higher than 'read', but only for nonempty locksets
    if (access.compareTo(o.access) > 0) {
      return false;
    }

    for (int i = 0; i < compatibleNodes.size(); i++) {
      CompatibleNode node = compatibleNodes.get(i);
      CompatibleNode otherNode = o.compatibleNodes.get(i);
      if (!node.cover(otherNode)) {
        return false;
      }
    }
    return true;
  }

  public boolean isCompatible(UsagePoint other) {
    for (int i = 0; i < compatibleNodes.size(); i++) {
      CompatibleNode node = compatibleNodes.get(i);
      CompatibleNode otherNode = other.compatibleNodes.get(i);
      if (!node.isCompatibleWith(otherNode)) {
        return false;
      }
    }
    return true;
  }

  public boolean isEmpty() {
    return from(compatibleNodes).allMatch(CompatibleNode::hasEmptyLockSet);
  }

  @Override
  public String toString() {
    return access + ":" + compatibleNodes;
  }

  public <T extends CompatibleNode> T get(Class<T> pClass) {
    for (CompatibleNode node : compatibleNodes) {
      if (node.getClass() == pClass) {
        return pClass.cast(node);
      }
    }
    return null;
  }

  public List<CompatibleNode> getCompatibleNodes() {
    return compatibleNodes;
  }
}
