/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting;

/** Metadata shared by built-in requirements, without wrapping their execution/type identities. */
public abstract class SelectorRequirement implements Requirement {
    private String selectorTag;
    private int triggerTime;
    private boolean triggerRepeatable;
    private boolean ignoreOutputCheck;

    @Override public String getSelectorTag() { return selectorTag; }
    @Override public void setSelectorTag(String tag) { selectorTag = tag == null || tag.isEmpty() ? null : tag; }

    @Override public int getTriggerTime() { return triggerTime; }
    @Override public boolean isTriggerRepeatable() { return triggerRepeatable; }
    @Override public void setTriggerTime(int tick) { triggerTime = Math.max(0, tick); }
    @Override public void setTriggerRepeatable(boolean repeatable) { triggerRepeatable = repeatable; }
    @Override public boolean isIgnoreOutputCheck() { return ignoreOutputCheck; }
    @Override public void setIgnoreOutputCheck(boolean ignored) { ignoreOutputCheck = ignored; }

    protected <T extends Requirement> T copySelectorTo(T copy) {
        copy.setSelectorTag(selectorTag);
        copy.setTriggerTime(triggerTime);
        copy.setTriggerRepeatable(triggerRepeatable);
        copy.setIgnoreOutputCheck(ignoreOutputCheck);
        if (this instanceof Requirement.Parallelizable source && copy instanceof Requirement.Parallelizable target) {
            target.setParallelizeUnaffected(source.isParallelizeUnaffected());
            target.setParallelism(source.getParallelism());
        }
        return copy;
    }
}
