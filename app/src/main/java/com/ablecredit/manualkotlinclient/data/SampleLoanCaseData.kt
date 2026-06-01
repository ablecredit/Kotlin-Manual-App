package com.ablecredit.manualkotlinclient.data

object SampleLoanCaseData {

    fun buildCreateLoanPayload(
        loanReference: String,
        userName: String,
        branchName: String,
        product: String,
        businessModel: String
    ): Map<String, Any> {
        val borrowerDetails = mapOf(
            "state_name" to "karnataka",
            "entity_type" to "individual",
            "name" to "Shwetanka Srivastava",
            "dob" to "24/01/1988",
            "mobile" to "8197837043",
            "owner_of_business" to "Yes"
        )

        val coBorrower = mapOf(
            "entity_type" to "individual",
            "name" to "Shwetanka Srivastava",
            "dob" to "24/01/1988",
            "relation" to "Brother",
            "occupation" to "IT Professional",
            "owner_of_business" to "No"
        )

        val employmentDetails = mapOf(
            "employer_name" to "optimus",
            "employer_contact_number" to "9873654210",
            "doj" to "14/10/2020",
            "nature_of_employment" to "Full-Time",
            "total_experience" to "36",
            "working_location" to "#23, Bangalore",
            "working_days_in_month" to "28",
            "per_day_earnings" to "500",
            "per_month_earnings" to "30000"
        )

        val loanDetails = mapOf(
            "business_name" to "trends",
            "quantum" to "500000",
            "tenure" to "24"
        )

        val data = mapOf(
            "borrower_details" to borrowerDetails,
            "co_borrower_details" to listOf(coBorrower),
            "employment_details" to employmentDetails,
            "loan_details" to loanDetails
        )

        val businessProfile = mapOf(
            "product" to product,
            "business_model" to businessModel,
            "industry" to "Fashion Apparel"
        )

        return mapOf(
            "client_unique_id" to "CUST-20251017-1234",
            "product_id" to "MUT-IND-3065",
            "branch_id" to "ML1348",
            "source_system" to "",
            "user_name" to userName,
            "branch_name" to branchName,
            "loan_reference" to loanReference,
            "data" to data,
            "business_profile" to businessProfile
        )
    }
}
