package vinho.andre.android.com.gerenciadorvinho.data.firebase

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.storage.UploadTask
import vinho.andre.android.com.gerenciadorvinho.data.sqlite.DBHelper
import vinho.andre.android.com.gerenciadorvinho.domain.Comment
import vinho.andre.android.com.gerenciadorvinho.domain.ImageUtil
import vinho.andre.android.com.gerenciadorvinho.domain.Purchase
import vinho.andre.android.com.gerenciadorvinho.domain.Wine
import vinho.andre.android.com.gerenciadorvinho.domain.WineComplement
import vinho.andre.android.com.gerenciadorvinho.interfaces.data.WineRegisterDataInterface
import vinho.andre.android.com.gerenciadorvinho.interfaces.presenter.WineRegisterPresenterInterface
import java.io.File

class WineRegisterData(
    private val context: Context,
    private val image: ImageUtil?,
    private val isUpdateOf: String?,
    private val presenter: WineRegisterPresenterInterface
) : FirebaseBase(),
    WineRegisterDataInterface {

    private val batch: WriteBatch =
        getFireStore().batch()

    private var comment: Comment? = null
    private var purchase: Purchase? = null

    private lateinit var wine: Wine
    private lateinit var wineComplement: WineComplement

    override fun onSaveWine(
        wine: Wine,
        comment: Comment,
        purchase: Purchase,
        wineComplement: WineComplement
    ) {
        onPrepareWine(
            wine
        )

        onPreparePurchase(
            purchase
        )

        onPrepareComment(
            comment
        )

        onPrepareWineComplement(
            wineComplement
        )

        onCommitBatch()
    }

    override fun onSaveWine(
        wine: Wine,
        purchase: Purchase,
        wineComplement: WineComplement
    ) {
        onPrepareWine(
            wine
        )

        onPreparePurchase(
            purchase
        )

        onPrepareWineComplement(
            wineComplement
        )

        onCommitBatch()
    }

    override fun onSaveWine(
        wine: Wine,
        comment: Comment,
        wineComplement: WineComplement
    ) {
        onPrepareWine(
            wine
        )

        onPrepareComment(
            comment
        )

        onPrepareWineComplement(
            wineComplement
        )

        onCommitBatch()
    }

    override fun onSaveWine(
        wine: Wine,
        wineComplement: WineComplement
    ) {
        onPrepareWine(
            wine
        )

        onPrepareWineComplement(
            wineComplement
        )

        onCommitBatch()
    }

    override fun onUploadImage() {
        val storageReference =
            getStorageCollection(
                image!!.nameImage
            )

        val uploadTask =
            storageReference
                .putFile(
                    Uri.fromFile(
                        File(
                            image.currentPathImage
                        )
                    )
                )

        uploadTask
            .addOnProgressListener { taskSnapshot ->
                val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
                presenter.notifyProgressNotification(
                    progress.toInt()
                )
            }
            .continueWithTask(
                Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
                    if (!task.isSuccessful) {
                        task.exception?.let {
                            throw it
                        }
                    }
                    return@Continuation storageReference.downloadUrl
                }
            )
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSaveInformationImage(
                        task.result.toString()
                    )
                } else {
                    saveOnSqlite()
                    callViewAction()
                    presenter.notifyUploadResult(false)
                }
            }
    }

    private fun onDeleteImage(
        nameImage: String
    ) {
        getStorageCollection(
            nameImage
        ).delete().addOnCompleteListener(
            executor,
            OnCompleteListener {
                if (it.isSuccessful) {
                    presenter.notifyUploadResult(true)
                }

                callViewAction(
                    it
                )
            }
        )
    }

    private fun onCommitBatch() {
        batch.commit().addOnCompleteListener(
            executor,
            OnCompleteListener {
                if (image != null) {
                    presenter.onSaveImage()
                } else {
                    saveOnSqlite()
                    callViewAction(it)
                }
            }
        )
    }

    private fun onSaveInformationImage(
        urlDownloadImage: String
    ) {
        val map = mapOf(
            Wine.NameImage to image!!.nameImage,
            Wine.UrlImage to urlDownloadImage
        )

        getWinesCollection()
            .document(
                wine.wineId
            )
            .update(
                Wine.FieldImage,
                map
            )
            .addOnCompleteListener(
                executor,
                OnCompleteListener {
                    if (it.isSuccessful) {
                        wine.image = map

                        saveOnSqlite()

                        if (isUpdateOf != null &&
                            isUpdateOf == Wine.UpdateWine &&
                            image.nameOldImage.isNotBlank()
                        ) {
                            onDeleteImage(
                                image.nameOldImage
                            )
                        } else {
                            callViewAction(
                                it
                            )
                            presenter.notifyUploadResult(true)
                        }
                    } else {
                        onDeleteImage(
                            image.nameImage
                        )
                        presenter.notifyUploadResult(false)
                    }
                }
            )
    }

    private fun callViewAction(
        task: Task<Void>? = null
    ) {
        presenter.onWineRescueResponse(
            task,
            wine
        )
    }

    private fun onPrepareWine(
        wine: Wine
    ) {
        val wineReference: DocumentReference

        if (isUpdateOf == null) {
            wineReference =
                getWinesCollection().document()

            wine.wineId = wineReference.id

            batch.set(
                wineReference,
                wine.getMap()
            )
        } else if (isUpdateOf == Wine.UpdateWine) {
            wineReference =
                getWinesCollection()
                    .document(
                        wine.wineId
                    )

            batch.update(
                wineReference,
                wine.getMap()
            )
        }

        this.wine = wine
    }

    private fun onPrepareComment(
        comment: Comment
    ) {
        if (isUpdateOf != null && isUpdateOf == Comment.updateComment) {
            val commentReference =
                getWineCommentsCellection(
                    wine.wineId
                ).document(
                    comment.commentId
                )
            batch.update(commentReference, comment.getMap())
        } else {
            val commentReference =
                getWineCommentsCellection(
                    wine.wineId
                ).document()
            comment.commentId = commentReference.id
            batch.set(commentReference, comment.getMap())
        }

        this.comment = comment
    }

    private fun onPreparePurchase(
        purchase: Purchase
    ) {
        if (isUpdateOf != null && isUpdateOf == Purchase.updatePurchase) {
            val purchaseReference =
                getWinePurchasesCellection(
                    wine.wineId
                ).document(
                    purchase.purchaseId
                )
            batch.update(purchaseReference, purchase.getMap())
        } else {
            val purchaseReference =
                getWinePurchasesCellection(
                    wine.wineId
                ).document()
            purchase.purchaseId = purchaseReference.id
            batch.set(purchaseReference, purchase.getMap())
        }

        this.purchase = purchase
    }

    private fun onPrepareWineComplement(
        wineComplement: WineComplement
    ) {
        val wineComplementReference: DocumentReference

        if (isUpdateOf == null) {
            wineComplementReference =
                getWinesComplementsCollection(
                    wine.wineId
                ).document()

            wineComplement.wineComplementId = wineComplementReference.id
            batch.set(wineComplementReference, wineComplement.getMap())
        } else if (isUpdateOf == Wine.UpdateWine) {
            wineComplementReference = getWinesComplementsCollection(
                wine.wineId
            ).document(
                wineComplement.wineComplementId
            )
            batch.update(wineComplementReference, wineComplement.getMap())
        }

        this.wineComplement = wineComplement
    }

    private fun saveOnSqlite() {
        val sqlite = DBHelper(context)

        if (sqlite.hasWine(wine.wineId)) {
            sqlite.updateWine(wine)
            sqlite.updateWineComplement(
                wine.wineId,
                wineComplement
            )
        } else {
            sqlite.saveWine(wine)
            sqlite.saveWineComplement(
                wine.wineId,
                wineComplement
            )
        }

        if (purchase != null) {
            if (sqlite.hasPurchase(purchase!!.purchaseId)) {
                sqlite.updatePurchase(
                    wine.wineId,
                    purchase!!
                )
            } else {
                sqlite.savePurchase(
                    wine.wineId,
                    purchase!!
                )
            }
        }

        if (comment != null) {
            if (sqlite.hasComment(comment!!.commentId)) {
                sqlite.updateComment(
                    wine.wineId,
                    comment!!
                )
            } else {
                sqlite.saveComment(
                    wine.wineId,
                    comment!!
                )
            }
        }
    }
}