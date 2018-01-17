package com.yubico.webauthn.rp

import java.security.MessageDigest
import java.security.KeyPair

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.yubico.scala.util.JavaConverters._
import com.yubico.u2f.attestation.MetadataResolver
import com.yubico.u2f.attestation.MetadataObject
import com.yubico.u2f.attestation.MetadataService
import com.yubico.u2f.attestation.resolvers.SimpleResolver
import com.yubico.u2f.crypto.BouncyCastleCrypto
import com.yubico.u2f.data.messages.key.util.U2fB64Encoding
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.FinishRegistrationSteps
import com.yubico.webauthn.data.ArrayBuffer
import com.yubico.webauthn.data.AuthenticationExtensions
import com.yubico.webauthn.data.MakePublicKeyCredentialOptions
import com.yubico.webauthn.data.PublicKeyCredentialParameters
import com.yubico.webauthn.data.PublicKey
import com.yubico.webauthn.data.RelyingPartyIdentity
import com.yubico.webauthn.data.UserIdentity
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.AttestationObject
import com.yubico.webauthn.data.CollectedClientData
import com.yubico.webauthn.data.AuthenticatorData
import com.yubico.webauthn.data.Basic
import com.yubico.webauthn.data.SelfAttestation
import com.yubico.webauthn.data.impl.PublicKeyCredential
import com.yubico.webauthn.data.impl.AuthenticatorAttestationResponse
import com.yubico.webauthn.impl.FidoU2fAttestationStatementVerifier
import com.yubico.webauthn.test.TestAuthenticator
import com.yubico.webauthn.util.BinaryUtil
import com.yubico.webauthn.util.WebAuthnCodecs
import org.apache.commons.io.IOUtils
import org.junit.runner.RunWith
import org.scalacheck.Gen
import org.scalatest.FunSpec
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scala.collection.JavaConverters._
import scala.util.Failure
import scala.util.Success
import scala.util.Try


@RunWith(classOf[JUnitRunner])
class RelyingPartyRegistrationSpec extends FunSpec with Matchers with GeneratorDrivenPropertyChecks {

  def jsonFactory: JsonNodeFactory = JsonNodeFactory.instance

  object Defaults {
    // These values were generated using TestAuthenticator.makeCredentialExample(TestAuthenticator.createCredential())
    val attestationObject: ArrayBuffer = BinaryUtil.fromHex("bf68617574684461746158ac49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97634100000539000102030405060708090a0b0c0d0e0f0020fcf11b31e19931ba2ef2cffcf6c27a863de6ab43873d05bbd8f30c4fd69e15febf63616c67654553323536617858206a7f09e364f7dab64ec5a7a24aa5293acde8e722e555934e1e223bb0a9d1f398617958210091baa59d427eb615d0bf8cf8852cdb5ab6bcde2788bf5e017dc5f0fa91a972c7ff63666d74686669646f2d7532666761747453746d74bf637835639f5902fe308202fa308201e2a003020102020103300d06092a864886f70d01010b0500302c312a302806035504030c21576562417574686e20756e69742074657374206174746573746174696f6e204341301e170d3137303931313132353431395a170d3237303930393132353431395a308185310b30090603550406130253453112301006035504070c0953746f636b686f6c6d310f300d060355040a0c0659756269636f311c301a060355040b0c13576562417574686e20756e69742074657374733133303106035504030c2a576562417574686e20756e69742074657374206174746573746174696f6e2063657274696669636174653059301306072a8648ce3d020106082a8648ce3d030107034200046e8e20021f3b33f2f98876aeed34328d8b9fa226576e78e9f5675d3c68af4c24fca58e4f3a26675e9f027329dab2840fc327dafff5f78d81726d16fbbc0ebce2a3819730819430090603551d1304023000301d0603551d0e041604145df47f419caa95f3936fb9e52620ad8c319daa6630460603551d23043f303da130a42e302c312a302806035504030c21576562417574686e20756e69742074657374206174746573746174696f6e204341820900c4bf47d5aff768f730130603551d25040c300a06082b06010505070302300b0603551d0f040403020780300d06092a864886f70d01010b05000382010100bf46d0d0d87718d1b332a754375c6a5c0bc49ae46e728aedbd11e2b510ba90154df29d147f42bcb7762e31ebf33bfd7ab425c31712e58851e29bc997f83c8fd545ce03a05a9a07bdb45eb9c4579aaffd5205b763d9be2317e07e50c983fab7a8a3aea4e57e26c7ec0f33523d3b6eadac44ac6cb59c95108e7c1e8811b45a9e14de379a6d293dcda02ff210b5e2e23319c18e325fe521e53c3edacf0fa484fb51990193928d710e0bab0e682e5c0f89f21d2b1a47d8848b06f3b342ca03f47ad17b5703805d2d96f8797e5f132acc69c7764a0f5011638c2ddd37365c504a480b35a42557b7889d90d04a180c1432a6b3230127e27fc8b7f5dbe450dd4d3c5ce7ff6373696758473045022050deefc3e97a4b395fdd3465c59ed07ed2f76410fc9d0835e34835922df3da39022100f3f0c69da18d5ffb08f6b6d4be5dec97ec5e9c57d98ddfcddfbedcb86e437d1bffff").get
    val clientDataJson: String = """{"challenge":"AAEBAgMFCA0VIjdZEGl5Yls","origin":"localhost","hashAlgorithm":"SHA-256","type":"webauthn.create"}"""

    // These values are defined by the attestationObject and clientDataJson above
    val clientData = CollectedClientData(WebAuthnCodecs.json.readTree(clientDataJson))
    val clientDataJsonBytes: ArrayBuffer = clientDataJson.getBytes("UTF-8").toVector
    val challenge: ArrayBuffer = U2fB64Encoding.decode(clientData.challenge).toVector
    val requestedExtensions: Option[AuthenticationExtensions] = None
    val clientExtensionResults: AuthenticationExtensions = jsonFactory.objectNode()

    val rpId = RelyingPartyIdentity(name = "Test party", id = "localhost")
    val userId = UserIdentity(name = "test@test.org", displayName = "Test user", id = "test")

    val createCredentialOptions = MakePublicKeyCredentialOptions(
      rp = rpId,
      user = userId,
      challenge = challenge,
      pubKeyCredParams = List(PublicKeyCredentialParameters(alg = -7)).asJava
    )

  }

  def finishRegistration(
    allowSelfAttestation: Boolean = false,
    attestationObject: ArrayBuffer = Defaults.attestationObject,
    authenticatorRequirements: Option[AuthenticatorSelectionCriteria] = None,
    callerTokenBindingId: Option[String] = None,
    challenge: ArrayBuffer = Defaults.challenge,
    clientDataJson: String = Defaults.clientDataJson,
    clientExtensionResults: AuthenticationExtensions = Defaults.clientExtensionResults,
    credentialId: Option[ArrayBuffer] = None,
    metadataService: Option[MetadataService] = None,
    origin: String = Defaults.rpId.id,
    requestedExtensions: Option[AuthenticationExtensions] = Defaults.requestedExtensions,
    rpId: RelyingPartyIdentity = Defaults.rpId,
    userId: UserIdentity = Defaults.userId
  ): FinishRegistrationSteps = {
    val clientDataJsonBytes: ArrayBuffer = clientDataJson.getBytes("UTF-8").toVector

    val request = MakePublicKeyCredentialOptions(
      rp = rpId,
      user = userId,
      challenge = challenge,
      pubKeyCredParams = List(PublicKeyCredentialParameters(`type` = PublicKey, alg = -7L)).asJava,
      extensions = requestedExtensions.asJava
    )

    val response = PublicKeyCredential(
      credentialId getOrElse AttestationObject(attestationObject).authenticatorData.attestationData.get.credentialId,
      AuthenticatorAttestationResponse(attestationObject, clientDataJsonBytes),
      clientExtensionResults
    )

    new RelyingParty(
      allowSelfAttestation = allowSelfAttestation,
      authenticatorRequirements = authenticatorRequirements.asJava,
      challengeGenerator = null,
      origins = List(origin).asJava,
      preferredPubkeyParams = request.pubKeyCredParams,
      rp = rpId,
      credentialRepository = null,
      metadataService = metadataService.asJava
    )._finishRegistration(request, response, callerTokenBindingId.asJava)
  }

  describe("§7.1. Registering a new credential") {

    describe("When registering a new credential, represented by a AuthenticatorAttestationResponse structure, as part of a registration ceremony, a Relying Party MUST proceed as follows:") {

      it("1. Perform JSON deserialization on the clientDataJSON field of the AuthenticatorAttestationResponse object to extract the client data C claimed as collected during the credential creation.") {
        val steps = finishRegistration(clientDataJson = "{")
        val step1: steps.Step1 = steps.begin

        step1.validations shouldBe a [Failure[_]]
        step1.validations.failed.get shouldBe a [JsonParseException]
        step1.next shouldBe a [Failure[_]]
      }

      describe("2. Verify that the type in C is the string webauthn.create.") {
        it("The default test case succeeds.") {
          val steps = finishRegistration()
          val step: steps.Step4 = steps.begin.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
        }


        def assertFails(typeString: String): Unit = {
          val steps = finishRegistration(
            clientDataJson = WebAuthnCodecs.json.writeValueAsString(
              WebAuthnCodecs.json.readTree(Defaults.clientDataJson).asInstanceOf[ObjectNode]
                .set("type", jsonFactory.textNode(typeString))
            )
          )
          val step: steps.Step2 = steps.begin.next.get

          step.validations shouldBe a [Failure[_]]
          step.validations.failed.get shouldBe an [AssertionError]
        }

        it("""Any value other than "webauthn.create" fails.""") {
          forAll { (typeString: String) =>
            whenever (typeString != "webauthn.create") {
              assertFails(typeString)
            }
          }
          forAll(Gen.alphaNumStr) { (typeString: String) =>
            whenever (typeString != "webauthn.create") {
              assertFails(typeString)
            }
          }
        }
      }

      it("3. Verify that the challenge in C matches the challenge that was sent to the authenticator in the create() call.") {
        val steps = finishRegistration(challenge = Vector.fill(16)(0: Byte))
        val step: steps.Step3 = steps.begin.next.get.next.get

        step.validations shouldBe a [Failure[_]]
        step.validations.failed.get shouldBe an [AssertionError]
        step.next shouldBe a [Failure[_]]
      }

      it("4. Verify that the origin in C matches the Relying Party's origin.") {
        val steps = finishRegistration(origin = "root.evil")
        val step: steps.Step4 = steps.begin.next.get.next.get.next.get

        step.validations shouldBe a [Failure[_]]
        step.validations.failed.get shouldBe an [AssertionError]
        step.next shouldBe a [Failure[_]]
      }

      describe("5. Verify that the tokenBindingId in C matches the Token Binding ID for the TLS connection over which the attestation was obtained.") {
        it("Verification succeeds if neither side specifies token binding ID.") {
          val steps = finishRegistration()
          val step: steps.Step5 = steps.begin.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
        }

        it("Verification succeeds if both sides specify the same token binding ID.") {
          val clientDataJson = """{"challenge":"AAEBAgMFCA0VIjdZEGl5Yls","origin":"localhost","hashAlgorithm":"SHA-256","tokenBindingId":"YELLOWSUBMARINE","type":"webauthn.create"}"""

          val steps = finishRegistration(
            callerTokenBindingId = Some("YELLOWSUBMARINE"),
            clientDataJson = clientDataJson
          )
          val step: steps.Step5 = steps.begin.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
        }

        it("Verification fails if caller specifies token binding ID but attestation does not.") {
          val steps = finishRegistration(callerTokenBindingId = Some("YELLOWSUBMARINE"))
          val step: steps.Step5 = steps.begin.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Failure[_]]
          step.validations.failed.get shouldBe an [AssertionError]
          step.next shouldBe a [Failure[_]]
        }

        it("Verification fails if attestation specifies token binding ID but caller does not.") {
          val attestationObjectBytes: ArrayBuffer = BinaryUtil.fromHex("bf68617574684461746158ab49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97634100000539000102030405060708090a0b0c0d0e0f002067d2e4a43d68158d5be9786d7a708c94782669deda891bda4a586c1331e1d7bebf63616c67654553323536617858201f228113a2cc82ad4633ff58dffe09c8d28177f11590b737d1a13f628db33721617958207721f99e5ff74631df92d1c3ebc758e821cd1c7b323946d97f4ff43083cf0b2fff63666d74686669646f2d7532666761747453746d74bf637835639f59013b308201373081dea00302010202020539300a06082a8648ce3d04030230253123302106035504030c1a59756269636f20576562417574686e20756e6974207465737473301e170d3138303930363137343230305a170d3138303930363137343230305a30253123302106035504030c1a59756269636f20576562417574686e20756e69742074657374733059301306072a8648ce3d020106082a8648ce3d0301070342000485a43c1f4e2e625cc3ce85f0a7b827b1358be9c1be9d45fba1632e5e7f4d1db5488bd7a6101ae16457cb12a3d3408b989993e017c027e1af43624bdec1402e6e300a06082a8648ce3d040302034800304502205298725d18bd9645a8118f42f4a9a9fa49396851305c1bff3da01f29fc704656022100d93a584a1273b695f0c7497bf6fcc1a9ecbe29376c00a4abb25ff7af48a92ccfff63736967584630440220188eb445f56aa23f3be2f7b327cca187a34fd300af3d3c985fda6a8829f770440220340f2029d42d48bc021341d7054ee708a5d9223580faac6530990de8a5775a53ffff").get
          val clientDataJson = """{"challenge":"AAEBAgMFCA0VIjdZEGl5Yls","origin":"localhost","hashAlgorithm":"SHA-256","tokenBindingId":"YELLOWSUBMARINE","type":"webauthn.create"}"""

          val steps = finishRegistration(
            callerTokenBindingId = None,
            attestationObject = attestationObjectBytes,
            clientDataJson = clientDataJson
          )
          val step: steps.Step5 = steps.begin.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Failure[_]]
          step.validations.failed.get shouldBe an [AssertionError]
          step.next shouldBe a [Failure[_]]
        }

        it("Verification fails if attestation and caller specify different token binding IDs.") {
          val attestationObjectBytes: ArrayBuffer = BinaryUtil.fromHex("bf68617574684461746158ab49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97634100000539000102030405060708090a0b0c0d0e0f002067d2e4a43d68158d5be9786d7a708c94782669deda891bda4a586c1331e1d7bebf63616c67654553323536617858201f228113a2cc82ad4633ff58dffe09c8d28177f11590b737d1a13f628db33721617958207721f99e5ff74631df92d1c3ebc758e821cd1c7b323946d97f4ff43083cf0b2fff63666d74686669646f2d7532666761747453746d74bf637835639f59013b308201373081dea00302010202020539300a06082a8648ce3d04030230253123302106035504030c1a59756269636f20576562417574686e20756e6974207465737473301e170d3138303930363137343230305a170d3138303930363137343230305a30253123302106035504030c1a59756269636f20576562417574686e20756e69742074657374733059301306072a8648ce3d020106082a8648ce3d0301070342000485a43c1f4e2e625cc3ce85f0a7b827b1358be9c1be9d45fba1632e5e7f4d1db5488bd7a6101ae16457cb12a3d3408b989993e017c027e1af43624bdec1402e6e300a06082a8648ce3d040302034800304502205298725d18bd9645a8118f42f4a9a9fa49396851305c1bff3da01f29fc704656022100d93a584a1273b695f0c7497bf6fcc1a9ecbe29376c00a4abb25ff7af48a92ccfff63736967584630440220188eb445f56aa23f3be2f7b327cca187a34fd300af3d3c985fda6a8829f770440220340f2029d42d48bc021341d7054ee708a5d9223580faac6530990de8a5775a53ffff").get
          val clientDataJson = """{"challenge":"AAEBAgMFCA0VIjdZEGl5Yls","origin":"localhost","hashAlgorithm":"SHA-256","tokenBindingId":"YELLOWSUBMARINE","type":"webauthn.create"}"""

          val steps = finishRegistration(
            callerTokenBindingId = Some("ORANGESUBMARINE"),
            attestationObject = attestationObjectBytes,
            clientDataJson = clientDataJson
          )
          val step: steps.Step5 = steps.begin.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Failure[_]]
          step.validations.failed.get shouldBe an [AssertionError]
          step.next shouldBe a [Failure[_]]
        }
      }

      describe("6. Verify that the") {
        it("clientExtensions in C is a subset of the extensions requested by the RP.") {
          val failSteps = finishRegistration(
            clientDataJson =
              WebAuthnCodecs.json.writeValueAsString(
                WebAuthnCodecs.json.readTree(Defaults.clientDataJson).asInstanceOf[ObjectNode]
                  .set("clientExtensions", jsonFactory.objectNode().set("foo", jsonFactory.textNode("boo")))
              )
          )
          val failStep: failSteps.Step6 = failSteps.begin.next.get.next.get.next.get.next.get.next.get

          failStep.validations shouldBe a [Failure[_]]
          failStep.validations.failed.get shouldBe an[AssertionError]
          failStep.next shouldBe a [Failure[_]]

          val successSteps = finishRegistration(
            requestedExtensions = Some(jsonFactory.objectNode().set("foo", jsonFactory.textNode("bar"))),
            clientDataJson =
              WebAuthnCodecs.json.writeValueAsString(
                WebAuthnCodecs.json.readTree(Defaults.clientDataJson).asInstanceOf[ObjectNode]
                  .set("clientExtensions", jsonFactory.objectNode().set("foo", jsonFactory.textNode("boo")))
              )
          )
          val successStep: successSteps.Step6 = successSteps.begin.next.get.next.get.next.get.next.get.next.get

          successStep.validations shouldBe a [Success[_]]
          successStep.next shouldBe a [Success[_]]
        }

        it("authenticatorExtensions in C is also a subset of the extensions requested by the RP.") {
          val failSteps = finishRegistration(
            clientDataJson =
              WebAuthnCodecs.json.writeValueAsString(
                WebAuthnCodecs.json.readTree(Defaults.clientDataJson).asInstanceOf[ObjectNode]
                  .set("authenticatorExtensions", jsonFactory.objectNode().set("foo", jsonFactory.textNode("boo")))
              )
          )
          val failStep: failSteps.Step6 = failSteps.begin.next.get.next.get.next.get.next.get.next.get

          failStep.validations shouldBe a [Failure[_]]
          failStep.validations.failed.get shouldBe an[AssertionError]
          failStep.next shouldBe a [Failure[_]]

          val successSteps = finishRegistration(
            requestedExtensions = Some(jsonFactory.objectNode().set("foo", jsonFactory.textNode("bar"))),
            clientDataJson =
              WebAuthnCodecs.json.writeValueAsString(
                WebAuthnCodecs.json.readTree(Defaults.clientDataJson).asInstanceOf[ObjectNode]
                  .set("authenticatorExtensions", jsonFactory.objectNode().set("foo", jsonFactory.textNode("boo")))
              )
          )
          val successStep: successSteps.Step6 = successSteps.begin.next.get.next.get.next.get.next.get.next.get

          successStep.validations shouldBe a [Success[_]]
          successStep.next shouldBe a [Success[_]]
        }
      }

      describe("7. Compute the hash of clientDataJSON using the algorithm identified by C.hashAlgorithm.") {
        it("SHA-256 is allowed.") {
          val steps = finishRegistration()
          val step: steps.Step7 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
          step.clientDataJsonHash should equal (MessageDigest.getInstance("SHA-256").digest(Defaults.clientDataJsonBytes.toArray).toVector)
        }

        def checkForbidden(algorithm: String): Unit = {
          it(s"${algorithm} is forbidden.") {
            val steps = finishRegistration(
              clientDataJson =
                WebAuthnCodecs.json.writeValueAsString(
                  WebAuthnCodecs.json.readTree(Defaults.clientDataJson).asInstanceOf[ObjectNode]
                    .set("hashAlgorithm", jsonFactory.textNode(algorithm))
                )
            )
            val step: steps.Step7 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get

            step.validations shouldBe a [Failure[_]]
            step.validations.failed.get shouldBe an [AssertionError]
            step.next shouldBe a [Failure[_]]
          }
        }
        checkForbidden("MD5")
        checkForbidden("SHA1")
      }

      it("8. Perform CBOR decoding on the attestationObject field of the AuthenticatorAttestationResponse structure to obtain the attestation statement format fmt, the authenticator data authData, and the attestation statement attStmt.") {
        val steps = finishRegistration()
        val step: steps.Step8 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get

        step.validations shouldBe a [Success[_]]
        step.next shouldBe a [Success[_]]
        step.attestation.format should equal ("fido-u2f")
        step.attestation.authenticatorData should not be null
        step.attestation.attestationStatement should not be null
      }

      describe("9. Verify that the RP ID hash in authData is indeed the SHA-256 hash of the RP ID expected by the RP.") {
        it("Fails if RP ID is different.") {
          val steps = finishRegistration(rpId = Defaults.rpId.copy(id = "root.evil"))
          val step: steps.Step9 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Failure[_]]
          step.validations.failed.get shouldBe an [AssertionError]
          step.next shouldBe a [Failure[_]]
        }

        it("Succeeds if RP ID is the same.") {
          val steps = finishRegistration()
          val step: steps.Step9 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
        }
      }

      describe("10. Determine the attestation statement format by performing an USASCII case-sensitive match on fmt against the set of supported WebAuthn Attestation Statement Format Identifier values. The up-to-date list of registered WebAuthn Attestation Statement Format Identifier values is maintained in the in the IANA registry of the same name [WebAuthn-Registries].") {
        def setup(format: String): FinishRegistrationSteps = {
          val attestationObject: ArrayBuffer = WebAuthnCodecs.cbor.writeValueAsBytes(
            WebAuthnCodecs.cbor.readTree(Defaults.attestationObject.toArray)
              .asInstanceOf[ObjectNode]
              .set("fmt", jsonFactory.textNode(format))
          ).toVector

          finishRegistration(attestationObject = attestationObject)
        }

        def checkFailure(format: String): Unit = {
          it(s"""Fails if fmt is "${format}".""") {
            val steps = setup(format)
            val step: steps.Step10 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

            step.validations shouldBe a [Failure[_]]
            step.validations.failed.get shouldBe an [AssertionError]
            step.next shouldBe a [Failure[_]]
          }
        }

        def checkSuccess(format: String): Unit = {
          it(s"""Succeeds if fmt is "${format}".""") {
            val steps = setup(format)
            val step: steps.Step10 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

            step.validations shouldBe a [Success[_]]
            step.next shouldBe a [Success[_]]
            step.format should equal (format)
            step.formatSupported should be(true)
          }
        }

        checkSuccess("android-key")
        checkSuccess("android-safetynet")
        checkSuccess("fido-u2f")
        checkSuccess("packed")
        checkSuccess("tpm")

        checkFailure("FIDO-U2F")
        checkFailure("Fido-U2F")
        checkFailure("bleurgh")
      }

      describe("11. Verify that attStmt is a correct attestation statement, conveying a valid attestation signature, by using the attestation statement format fmt’s verification procedure given attStmt, authData and the hash of the serialized client data computed in step 6.") {

        describe("For the fido-u2f statement format,") {
          it("the default test case is a valid basic attestation.") {
            val steps = finishRegistration()
            val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

            step.validations shouldBe a [Success[_]]
            step.attestationType should equal (Basic)
            step.next shouldBe a [Success[_]]
          }

          it("a test case with self attestation is valid.") {
            val attestationObject = BinaryUtil.fromHex("bf68617574684461746158ac49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97634100000539000102030405060708090a0b0c0d0e0f0020b6301bd05fa254360b23ceede039fa0ac97b74ab6f8241c1cce225b521f92f61bf63616c67654553323536617858203f735d9a1b48a08c099f8ab5c6824465a13d7aea552f038fbc886e5f4916910861795821008192a1ecfcfcb285050fbbb49c1f7ecbfb0eb7558782c271480bc656b2f473c1ff63666d74686669646f2d7532666761747453746d74bf637835639f59013b308201373081dea00302010202020539300a06082a8648ce3d04030230253123302106035504030c1a59756269636f20576562417574686e20756e6974207465737473301e170d3138303930363137343230305a170d3138303930363137343230305a30253123302106035504030c1a59756269636f20576562417574686e20756e69742074657374733059301306072a8648ce3d020106082a8648ce3d030107034200043f735d9a1b48a08c099f8ab5c6824465a13d7aea552f038fbc886e5f491691088192a1ecfcfcb285050fbbb49c1f7ecbfb0eb7558782c271480bc656b2f473c1300a06082a8648ce3d0403020348003045022100f3f63e97ae36b181a8e2ca3d8bb60201c80f089c241e28c59a61302a1638f7870220116b43eeb54e7192f5ce9a0d018410ac0c608bc53ea2e1cbecdad9910689fcd6ff63736967584730450221009d6277e2300f7542107941ef1915613a0fbba66a27c37c56559d8817236c66250220632eadc777524e0032e2731275ccf06e6021a44c7804993cb894d8546cd9727effff").get

            val steps = finishRegistration(attestationObject = attestationObject)
            val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

            step.validations shouldBe a [Success[_]]
            step.attestationType should equal (SelfAttestation)
            step.next shouldBe a [Success[_]]
          }

          def flipByte(index: Int, bytes: ArrayBuffer): ArrayBuffer = bytes.updated(index, (0xff ^ bytes(index)).toByte)

          it("a test case with different signed client data is not valid.") {
            val steps = finishRegistration()
            val step: steps.Step11 = new steps.Step11(
              attestation = AttestationObject(Defaults.attestationObject),
              clientDataJsonHash = new BouncyCastleCrypto().hash(Defaults.clientDataJsonBytes.updated(20, (Defaults.clientDataJsonBytes(20) + 1).toByte).toArray).toVector,
              attestationStatementVerifier = FidoU2fAttestationStatementVerifier
            )

            step.validations shouldBe a [Failure[_]]
            step.validations.failed.get shouldBe an [AssertionError]
            step.next shouldBe a [Failure[_]]
          }

          def mutateAuthenticatorData(attestationObject: ArrayBuffer)(mutator: ArrayBuffer => ArrayBuffer): ArrayBuffer = {
            val decodedCbor: ObjectNode = WebAuthnCodecs.cbor.readTree(attestationObject.toArray).asInstanceOf[ObjectNode]
            decodedCbor.set("authData", jsonFactory.binaryNode(mutator(decodedCbor.get("authData").binaryValue().toVector).toArray))

            WebAuthnCodecs.cbor.writeValueAsBytes(decodedCbor).toVector
          }

          def checkByteFlipFails(index: Int): Unit = {
            val attestationObject = mutateAuthenticatorData(Defaults.attestationObject) {
              flipByte(index, _)
            }
            val steps = finishRegistration(
              attestationObject = attestationObject,
              credentialId = Some(Vector.fill[Byte](16)(0))
            )
            val step: steps.Step11 = new steps.Step11(
              attestation = AttestationObject(attestationObject),
              clientDataJsonHash = new BouncyCastleCrypto().hash(Defaults.clientDataJsonBytes.toArray).toVector,
              attestationStatementVerifier = FidoU2fAttestationStatementVerifier
            )

            step.validations shouldBe a [Failure[_]]
            step.validations.failed.get shouldBe an [AssertionError]
            step.next shouldBe a [Failure[_]]
          }

          it("a test case with a different signed RP ID hash is not valid.") {
            checkByteFlipFails(0)
          }

          it("a test case with a different signed credential ID is not valid.") {
            checkByteFlipFails(32 + 1 + 4 + 16 + 2 + 1)
          }

          it("a test case with a different signed credential public key is not valid.") {
            val attestationObject = mutateAuthenticatorData(Defaults.attestationObject) { authenticatorData =>
              val decoded = AuthenticatorData(authenticatorData)
              val L = decoded.attestationData.get.credentialId.length
              val evilPublicKey = decoded.attestationData.get.credentialPublicKey.asInstanceOf[ObjectNode]
                .set("x", jsonFactory.binaryNode(Array.fill[Byte](32)(0)))

              authenticatorData.take(32 + 1 + 4 + 16 + 2 + L) ++ WebAuthnCodecs.cbor.writeValueAsBytes(evilPublicKey)
            }
            val steps = finishRegistration(
              attestationObject = attestationObject,
              credentialId = Some(Vector.fill[Byte](16)(0))
            )
            val step: steps.Step11 = new steps.Step11(
              attestation = AttestationObject(attestationObject),
              clientDataJsonHash = new BouncyCastleCrypto().hash(Defaults.clientDataJsonBytes.toArray).toVector,
              attestationStatementVerifier = FidoU2fAttestationStatementVerifier
            )

            step.validations shouldBe a [Failure[_]]
            step.validations.failed.get shouldBe an [AssertionError]
            step.next shouldBe a [Failure[_]]
          }

          describe("if x5c is not a certificate for an ECDSA public key over the P-256 curve, stop verification and return an error.") {
            val testAuthenticator = new TestAuthenticator()

            def checkRejected(keypair: KeyPair): Unit = {
              val credential = testAuthenticator.createCredential(attestationCertAndKey = Some(testAuthenticator.generateAttestationCertificate(keypair)))

              val steps = finishRegistration(
                attestationObject = credential.response.attestationObject,
                credentialId = Some(credential.rawId),
                clientDataJson = new String(credential.response.clientDataJSON.toArray, "UTF-8")
              )
              val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

              val standaloneVerification = Try {
                FidoU2fAttestationStatementVerifier.verifyAttestationSignature(
                  credential.response.attestation,
                  new BouncyCastleCrypto().hash(credential.response.clientDataJSON.toArray).toVector
                )
              }

              step.validations shouldBe a [Failure[_]]
              step.validations.failed.get shouldBe an [AssertionError]
              step.next shouldBe a [Failure[_]]

              standaloneVerification shouldBe a [Failure[_]]
              standaloneVerification.failed.get shouldBe an [AssertionError]
            }

            def checkAccepted(keypair: KeyPair): Unit = {
              val credential = testAuthenticator.createCredential(attestationCertAndKey = Some(testAuthenticator.generateAttestationCertificate(keypair)))

              val steps = finishRegistration(
                attestationObject = credential.response.attestationObject,
                credentialId = Some(credential.rawId),
                clientDataJson = new String(credential.response.clientDataJSON.toArray, "UTF-8")
              )
              val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

              val standaloneVerification = Try {
                FidoU2fAttestationStatementVerifier.verifyAttestationSignature(
                  credential.response.attestation,
                  new BouncyCastleCrypto().hash(credential.response.clientDataJSON.toArray).toVector
                )
              }

              step.validations shouldBe a [Success[_]]
              step.next shouldBe a [Success[_]]

              standaloneVerification should equal (Success(true))
            }

            it("An RSA attestation certificate is rejected.") {
              checkRejected(testAuthenticator.generateRsaKeypair())
            }

            it("A secp256r1 attestation certificate is accepted.") {
              checkAccepted(testAuthenticator.generateEcKeypair(curve = "secp256r1"))
            }

            it("A secp256k1 attestation certificate is rejected.") {
              checkRejected(testAuthenticator.generateEcKeypair(curve = "secp256k1"))
            }

            it("A P-256 attestation certificate is accepted.") {
              checkAccepted(testAuthenticator.generateEcKeypair(curve = "P-256"))
            }
          }
        }

        it("The packed statement format is supported.") {
          val attestationObject: ArrayBuffer = WebAuthnCodecs.cbor.writeValueAsBytes(
            WebAuthnCodecs.cbor.readTree(Defaults.attestationObject.toArray).asInstanceOf[ObjectNode]
              .set("fmt", jsonFactory.textNode("packed"))
          ).toVector
          val steps = finishRegistration(attestationObject = attestationObject)
          val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
        }

        it("The tpm statement format is supported.") {
          val attestationObject: ArrayBuffer = WebAuthnCodecs.cbor.writeValueAsBytes(
            WebAuthnCodecs.cbor.readTree(Defaults.attestationObject.toArray).asInstanceOf[ObjectNode]
              .set("fmt", jsonFactory.textNode("tpm"))
          ).toVector
          val steps = finishRegistration(attestationObject = attestationObject)
          val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
        }

        it("The android-key statement format is supported.") {
          val attestationObject: ArrayBuffer = WebAuthnCodecs.cbor.writeValueAsBytes(
            WebAuthnCodecs.cbor.readTree(Defaults.attestationObject.toArray).asInstanceOf[ObjectNode]
              .set("fmt", jsonFactory.textNode("android-key"))
          ).toVector
          val steps = finishRegistration(attestationObject = attestationObject)
          val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
        }

        it("The android-safetynet statement format is supported.") {
          val attestationObject: ArrayBuffer = WebAuthnCodecs.cbor.writeValueAsBytes(
            WebAuthnCodecs.cbor.readTree(Defaults.attestationObject.toArray).asInstanceOf[ObjectNode]
              .set("fmt", jsonFactory.textNode("android-safetynet"))
          ).toVector
          val steps = finishRegistration(attestationObject = attestationObject)
          val step: steps.Step11 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

          step.validations shouldBe a [Success[_]]
          step.next shouldBe a [Success[_]]
        }
      }

      describe("12. If validation is successful, obtain a list of acceptable trust anchors (attestation root certificates or ECDAA-Issuer public keys) for that attestation type and attestation statement format fmt, from a trusted source or from policy. For example, the FIDO Metadata Service [FIDOMetadataService] provides one way to obtain such information, using the aaguid in the attestedCredentialData in authData.") {

        describe("For the fido-u2f statement format") {

          it("with self attestation, no trust anchors are returned.") {
            val attestationObject = BinaryUtil.fromHex("bf68617574684461746158ac49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97634100000539000102030405060708090a0b0c0d0e0f0020b6301bd05fa254360b23ceede039fa0ac97b74ab6f8241c1cce225b521f92f61bf63616c67654553323536617858203f735d9a1b48a08c099f8ab5c6824465a13d7aea552f038fbc886e5f4916910861795821008192a1ecfcfcb285050fbbb49c1f7ecbfb0eb7558782c271480bc656b2f473c1ff63666d74686669646f2d7532666761747453746d74bf637835639f59013b308201373081dea00302010202020539300a06082a8648ce3d04030230253123302106035504030c1a59756269636f20576562417574686e20756e6974207465737473301e170d3138303930363137343230305a170d3138303930363137343230305a30253123302106035504030c1a59756269636f20576562417574686e20756e69742074657374733059301306072a8648ce3d020106082a8648ce3d030107034200043f735d9a1b48a08c099f8ab5c6824465a13d7aea552f038fbc886e5f491691088192a1ecfcfcb285050fbbb49c1f7ecbfb0eb7558782c271480bc656b2f473c1300a06082a8648ce3d0403020348003045022100f3f63e97ae36b181a8e2ca3d8bb60201c80f089c241e28c59a61302a1638f7870220116b43eeb54e7192f5ce9a0d018410ac0c608bc53ea2e1cbecdad9910689fcd6ff63736967584730450221009d6277e2300f7542107941ef1915613a0fbba66a27c37c56559d8817236c66250220632eadc777524e0032e2731275ccf06e6021a44c7804993cb894d8546cd9727effff").get
            val steps = finishRegistration(attestationObject = attestationObject)
            val step: steps.Step12 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

            step.validations shouldBe a [Success[_]]
            step.trustResolver.asScala shouldBe empty
            step.next shouldBe a [Success[_]]
          }

          it("with basic attestation, a trust resolver is returned.") {
            val metadataResolver: MetadataResolver = new SimpleResolver
            val metadataService: MetadataService = new MetadataService(metadataResolver, null, null)
            val steps = finishRegistration(metadataService = Some(metadataService))
            val step: steps.Step12 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

            step.validations shouldBe a [Success[_]]
            step.trustResolver.get should not be null
            step.next shouldBe a [Success[_]]
          }

        }

      }

      describe("13. Assess the attestation trustworthiness using the outputs of the verification procedure in step 10, as follows:") {

        describe("If self attestation was used, check if self attestation is acceptable under Relying Party policy.") {

          describe("The default test case, with self attestation,") {
            val attestationObject = BinaryUtil.fromHex("bf68617574684461746158ac49960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97634100000539000102030405060708090a0b0c0d0e0f0020b6301bd05fa254360b23ceede039fa0ac97b74ab6f8241c1cce225b521f92f61bf63616c67654553323536617858203f735d9a1b48a08c099f8ab5c6824465a13d7aea552f038fbc886e5f4916910861795821008192a1ecfcfcb285050fbbb49c1f7ecbfb0eb7558782c271480bc656b2f473c1ff63666d74686669646f2d7532666761747453746d74bf637835639f59013b308201373081dea00302010202020539300a06082a8648ce3d04030230253123302106035504030c1a59756269636f20576562417574686e20756e6974207465737473301e170d3138303930363137343230305a170d3138303930363137343230305a30253123302106035504030c1a59756269636f20576562417574686e20756e69742074657374733059301306072a8648ce3d020106082a8648ce3d030107034200043f735d9a1b48a08c099f8ab5c6824465a13d7aea552f038fbc886e5f491691088192a1ecfcfcb285050fbbb49c1f7ecbfb0eb7558782c271480bc656b2f473c1300a06082a8648ce3d0403020348003045022100f3f63e97ae36b181a8e2ca3d8bb60201c80f089c241e28c59a61302a1638f7870220116b43eeb54e7192f5ce9a0d018410ac0c608bc53ea2e1cbecdad9910689fcd6ff63736967584730450221009d6277e2300f7542107941ef1915613a0fbba66a27c37c56559d8817236c66250220632eadc777524e0032e2731275ccf06e6021a44c7804993cb894d8546cd9727effff").get

            it("is rejected if self attestation is not allowed.") {
              val steps = finishRegistration(
                allowSelfAttestation = false,
                attestationObject = attestationObject
              )
              val step: steps.Step13 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

              step.validations shouldBe a [Failure[_]]
              step.validations.failed.get shouldBe an [AssertionError]
              step.attestationTrusted should be (false)
              step.next shouldBe a [Failure[_]]
            }

            it("is accepted if self attestation is allowed.") {
              val steps = finishRegistration(
                allowSelfAttestation = true,
                attestationObject = attestationObject
              )
              val step: steps.Step13 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

              step.validations shouldBe a [Success[_]]
              step.attestationTrusted should be (true)
              step.next shouldBe a [Success[_]]
            }
          }
        }

        it("If ECDAA was used, verify that the identifier of the ECDAA-Issuer public key used is included in the set of acceptable trust anchors obtained in step 11.") {
          fail("Not implemented.")
        }

        describe("Otherwise, use the X.509 certificates returned by the verification procedure to verify that the attestation public key correctly chains up to an acceptable root certificate.") {

          describe("A test case with basic attestation") {
            it("is rejected if trust cannot be derived from the trust anchors.") {
              val metadataResolver = new SimpleResolver
              val metadataService: MetadataService = new MetadataService(metadataResolver, null, null) // Stateful - do not share between tests
              val steps = finishRegistration(metadataService = Some(metadataService))
              val step: steps.Step13 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

              step.validations shouldBe a [Failure[_]]
              step.attestationTrusted should be (false)
              step.attestationMetadata.asScala should not be empty
              step.attestationMetadata.get.getMetadataIdentifier should be (null)
              step.next shouldBe a [Failure[_]]
            }

            it("is accepted if trust can be derived from the trust anchors.") {
              val metadataResolver = new SimpleResolver
              val metadataService: MetadataService = new MetadataService(metadataResolver, null, null) // Stateful - do not share between tests
              val attestationCaCertPem = IOUtils.toString(getClass.getResourceAsStream("/attestation-ca-cert.pem"), "UTF-8")

              metadataResolver.addMetadata(
                new MetadataObject(
                  jsonFactory.objectNode().setAll(Map(
                    "vendorInfo" -> jsonFactory.objectNode(),
                    "trustedCertificates" -> jsonFactory.arrayNode().add(jsonFactory.textNode(attestationCaCertPem)),
                    "devices" -> jsonFactory.arrayNode(),
                    "identifier" -> jsonFactory.textNode("Test attestation CA"),
                    "version" -> jsonFactory.numberNode(42)
                  ).asJava)
                )
              )

              val steps = finishRegistration(metadataService = Some(metadataService))
              val step: steps.Step13 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get

              step.validations shouldBe a [Success[_]]
              step.attestationTrusted should be (true)
              step.attestationMetadata.asScala should not be empty
              step.attestationMetadata.get.getMetadataIdentifier should equal ("Test attestation CA")
              step.next shouldBe a [Success[_]]
            }
          }

        }

      }

      describe("14. If the attestation statement attStmt verified successfully and is found to be trustworthy, then register the new credential with the account that was denoted in the options.user passed to create(), by associating it with the credentialId and credentialPublicKey in the attestedCredentialData in authData, as appropriate for the Relying Party's system.") {
        it("Nothing to test.") {}
      }

      describe("15. If the attestation statement attStmt successfully verified but is not trustworthy per step 12 above, the Relying Party SHOULD fail the registration ceremony.") {
        it("Nothing to test.") {}
      }

      describe("15. NOTE: However, if permitted by policy, the Relying Party MAY register the credential ID and credential public key but treat the credential as one with self attestation (see §6.3.3 Attestation Types). If doing so, the Relying Party is asserting there is no cryptographic proof that the public key credential has been generated by a particular authenticator model. See [FIDOSecRef] and [UAFProtocol] for a more detailed discussion.") {
        it("Nothing to test.") {}
      }

      it("15. (Deleted) If verification of the attestation statement failed, the Relying Party MUST fail the registration ceremony.") {
        val steps = finishRegistration(
          clientDataJson = WebAuthnCodecs.json.writeValueAsString(
            WebAuthnCodecs.json.readTree(Defaults.clientDataJson.getBytes("UTF-8")).asInstanceOf[ObjectNode]
              .set("foo", jsonFactory.textNode("bar"))
          )
        )
        val step10: steps.Step10 = steps.begin.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get.next.get
        val step11: steps.Step11 = step10.next.get

        step10.validations shouldBe a [Success[_]]
        step10.next shouldBe a [Success[_]]

        step11.validations shouldBe a [Failure[_]]
        step11.validations.failed.get shouldBe an [AssertionError]
        step11.next shouldBe a [Failure[_]]

        steps.run shouldBe a [Failure[_]]
        steps.run.failed.get shouldBe an [AssertionError]
      }

      it("To avoid ambiguity during authentication, the Relying Party SHOULD check that each credential is registered to no more than one user. If registration is requested for a credential that is already registered to a different user, the Relying Party SHOULD fail this ceremony, or it MAY decide to accept the registration, e.g. while deleting the older registration.") {
        fail("Not implemented.")
      }

    }

  }

}
