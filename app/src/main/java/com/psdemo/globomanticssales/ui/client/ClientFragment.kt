package com.psdemo.globomanticssales.ui.client

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.psdemo.globomanticssales.*
import kotlinx.android.synthetic.main.fragment_client.*
import java.io.File
import java.io.FileNotFoundException
import java.util.*

class ClientFragment : Fragment(), FilesAdapter.OnClickListener {

    private var clientId = 0
    private var  adapter = FilesAdapter(this)
    private lateinit var inputPDF: ParcelFileDescriptor

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_client, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val clientViewModel = ViewModelProvider(this).get(ClientViewModel::class.java)
        listFiles.layoutManager = LinearLayoutManager(activity)
        listFiles.adapter = adapter

        arguments?.let { bundle ->
            val passedArguments = ClientFragmentArgs.fromBundle(bundle)
            clientViewModel.getClient(passedArguments.clientId)
                .observe(viewLifecycleOwner, Observer { client ->
                    name.text = client.name
                    order.text = client.order
                    terms.text = client.terms
                    clientId = client.id

                    val calendar = Calendar.getInstance()
                    val dateFormat = DateFormat.getDateFormat(view.context)
                    calendar.timeInMillis = client.date
                    date.text = dateFormat.format(calendar.time)

                    adapter.setFiles(context!!.getFiles(clientId))

                    if (context!!.proposalExists(clientId)) {
                        btnProposal.visibility = INVISIBLE
                        btnPicture.visibility = VISIBLE
                    } else {
                        btnProposal.setOnClickListener {
                            context!!.buildPdf(client)
                            it.visibility = INVISIBLE
                            btnPicture.visibility = VISIBLE
                            adapter.setFiles(context!!.getFiles(clientId))
                        }
                    }

                    btnPicture.setOnClickListener {
                        val requestFileIntent = Intent(
                            Intent.ACTION_OPEN_DOCUMENT
                        ).apply {
                            type = "image/*"
                            addCategory(Intent.CATEGORY_OPENABLE)
                        }
                        startActivityForResult(requestFileIntent, PICTURE_REQUEST)
                    }
                })
        }
    }

    override fun onClick(file: File) {
        val uri: Uri? = try {
            FileProvider.getUriForFile(
                context!!,
                "com.psdemo.globomanticssales.fileprovider",
                file
            )
        }catch (e: IllegalArgumentException){
            Log.e("client fragment", "the selected file can't be shared: $file", e)
            null
        }

        if (uri != null){
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(
                uri,
                activity!!.contentResolver.getType(uri)
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)


        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, returnIntent: Intent?) {
        Log.e("ClientFragment:","requestcode: $requestCode resultCode: $resultCode")
        if (resultCode != Activity.RESULT_OK ||
                requestCode != PICTURE_REQUEST ||
                returnIntent == null){
            return
        }

        returnIntent.data?.also {
            returnUri ->
            inputPDF =  try {
                activity!!.contentResolver.openFileDescriptor(returnUri, "r")!!
            }catch (e: FileNotFoundException){
                Log.e("Client Fragment","File not Found", e)
            return
            }
        }

        val fileDescriptor = inputPDF.fileDescriptor
        val image: Bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        inputPDF.close()

        val input = TextInputEditText(context)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        input.layoutParams = layoutParams
        input.hint = "Picture Name"

        MaterialAlertDialogBuilder(context)
            .setTitle("Add Picture")
            .setView(input)
            .setPositiveButton("Save"){_,_ ->
                val name = input.text.toString()
                context!!.saveImage(image, name, clientId)
                adapter.setFiles(context!!.getFiles(clientId))
            }
            .setNegativeButton("Cancel", null)
            .show()

    }

    companion object{
        const val PICTURE_REQUEST = 120;
    }
}
