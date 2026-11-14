/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

/** No resource keys or inventory NBT: safe to expose as controller statistics. */
public record EStorageMatrixSummary(EStorageCellSpec specification, int usedTypes, long usedBytes, long amount) { }
