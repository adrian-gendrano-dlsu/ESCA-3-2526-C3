const { onValueCreated } = require("firebase-functions/v2/database")
const admin = require("firebase-admin")
admin.initializeApp()

// TEST EVENTS ONLY. Firebase Functions deployment is optional for database-sync testing.
exports.ThesisDemoHighRisk = onValueCreated(
  "/demo/drivers/driver1/events/{eventId}",
  async (event) => {
    const data = event.data.val()
    if (!data || data.simulated !== true || data.source !== "manual_test") return null
    if (data.severity !== "High" || data.livePushEligible !== true) return null
    const age = Date.now() - Number(data.timestampMs || 0)
    if (age < 0 || age > 30000) return null
    const snap = await admin.database().ref("/supervisorTokens").get()
    if (!snap.exists()) return null
    const tokens = Object.values(snap.val()).filter((v) => typeof v === "string" && v.length > 0)
    if (tokens.length === 0) return null
    return admin.messaging().sendEachForMulticast({
      tokens,
      data: {
        demo: "true",
        behavior: String(data.behavior || "Unknown behavior"),
        eventId: String(data.eventId || event.params.eventId)
      },
      android: { priority: "high" }
    })
  }
)
