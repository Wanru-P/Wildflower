export const documentValue = response => Array.isArray(response.data) ? response.data[0] : response.data;

/** Only create a missing ledger, atomically; never reset an existing cumulative budget. */
export async function initializeLedger(database, collection, seed) {
  return database.runTransaction(async tx => {
    const document = tx.collection(collection).doc('global-v1');
    if (documentValue(await document.get())) return false;
    const value = structuredClone(seed); delete value._id;
    await document.set(value);
    return true;
  });
}
